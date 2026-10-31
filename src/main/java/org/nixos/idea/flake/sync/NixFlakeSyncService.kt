package org.nixos.idea.flake.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.SyncViewManager
import com.intellij.build.events.MessageEvent
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nixos.idea.flake.NixFlakeActivityKey
import org.nixos.idea.flake.NixFlakeSettings
import org.nixos.idea.flake.model.NixFlakeImportState
import org.nixos.idea.flake.model.NixFlakeInputsRootsProvider
import org.nixos.idea.flake.model.NixFlakeWorkspaceModel
import java.nio.file.Path

/**
 * Runs the import ("sync") of linked flakes.
 *
 * The sync is shown in the "Sync" tab of the Build tool window with one child node per step.
 * The Gradle integration feeds the same view through `ExternalSystemEventDispatcher`; the fluent [BuildProgress]
 * API used here is the simpler alternative (see `BuildViewTest` in intellij-community for examples).
 *
 * Steps:
 *  1. Evaluate flake: `nix flake metadata --json` (description, lock file, store path of the flake)
 *  2. Fetch inputs: `nix flake archive --json` (makes sure all inputs are in the store, returns their store paths)
 *  3. Build formatter: `nix build .#formatter.<system>` (optional)
 *  4. Apply the result to [NixFlakeImportState], the Workspace Model, and the library roots.
 */
@Service(Service.Level.PROJECT)
class NixFlakeSyncService(private val project: Project, private val cs: CoroutineScope) {

    // TODO(decision): One mutex for all flakes keeps the Sync view readable. Per-flake locks would allow parallel imports.
    private val mutex = Mutex()

    /** Starts a sync in the background and returns immediately. Used by actions and the auto-import tracker. */
    fun scheduleSync(flakeRoot: String, explicit: Boolean) {
        cs.launch { sync(flakeRoot, explicit) }
    }

    fun scheduleSyncAll(explicit: Boolean) {
        for (root in NixFlakeSettings.getInstance(project).linkedFlakes) {
            scheduleSync(root, explicit)
        }
    }

    suspend fun sync(flakeRoot: String, explicit: Boolean) {
        // Never evaluate a flake of an untrusted project. Linking via NixFlakeUnlinkedProjectAware already asked the user;
        // this check covers auto-reload and actions. A `TrustedProjectsListener` (see NixFlakeTrustListener) re-runs the
        // sync once the user trusts the project, like Gradle's `ExternalSystemTrustedListener`.
        if (!TrustedProjects.isProjectTrusted(project)) {
            LOG.info("Skip sync of $flakeRoot: project is not trusted")
            return
        }
        project.trackActivity(NixFlakeActivityKey) {
            mutex.withLock {
                withBackgroundProgress(project, "Importing Nix flake") {
                    doSync(flakeRoot, explicit)
                }
            }
        }
    }

    private suspend fun doSync(flakeRoot: String, explicit: Boolean) {
        val publisher = project.messageBus.syncPublisher(TOPIC)
        publisher.syncStarted(flakeRoot)
        var success = false

        val buildDescriptor = DefaultBuildDescriptor(Any(), "Nix flake", flakeRoot, System.currentTimeMillis())
            .withRestartAction(ActionManager.getInstance().getAction("NixFlake.Reload"))
        // TODO(decision): Activate the Sync tab only on explicit syncs or failures, to not steal focus on auto-reload.
        //  `DefaultBuildDescriptor.withActivateToolWindowOnFailure/OnRun` or `BuildViewSettingsProvider` control this.
        val root = SyncViewManager.createBuildProgress(project).start(descriptor(buildDescriptor))
        try {
            val dir = Path.of(flakeRoot)
            val flake = NixFlakeImportState.FlakeState().apply { this.root = flakeRoot }

            step(root, "Evaluate flake") { step ->
                val metadata = parse(NixCommand.run(step, dir, "flake", "metadata", "--json", "path:$flakeRoot"))
                flake.description = metadata.str("description")
                flake.storePath = metadata.str("path")
                flake.lastModified = metadata.get("lastModified")?.asLong ?: 0L
                // TODO: `metadata.locks.nodes` has the locked refs and `flake = false` markers of all inputs.
                //  Merge them into the inputs collected in the next step (InputState.lockedRef, InputState.isFlake).
                // TODO(decision): `path:` copies the directory as-is. Using the plain directory makes nix use the git
                //  repository instead, which ignores untracked files (a common surprise). Which one do users expect?
            }

            step(root, "Fetch inputs") { step ->
                val archive = parse(NixCommand.run(step, dir, "flake", "archive", "--json", "path:$flakeRoot"))
                collectInputs(archive, prefix = "", into = flake.inputs)
            }

            step(root, "Build formatter") { step ->
                // TODO(decision): Is building the formatter part of the import, or done lazily on first format?
                //  It can download a lot. If it's part of the import, feed the out path into NixExternalFormatterSettings
                //  (per project, currently it is an application-level setting).
                val system = NixCommand.run(step, dir, "eval", "--impure", "--raw", "--expr", "builtins.currentSystem").trim()
                val hasFormatter = NixCommand.run(
                    step, dir, "eval", "--json", "path:$flakeRoot#formatter", "--apply", "f: f ? \"$system\"",
                ).trim() == "true"
                if (hasFormatter) {
                    flake.formatter = NixCommand.run(
                        step, dir, "build", "--no-link", "--print-out-paths", "path:$flakeRoot#formatter.$system",
                    ).trim().lines().firstOrNull()
                }
                else {
                    step.output("Flake has no formatter for $system\n", true)
                }
            }

            step(root, "Update project model") {
                apply(flake)
            }
            success = true
            root.finish()
        }
        catch (e: CancellationException) {
            root.cancel()
            throw e
        }
        catch (e: Exception) {
            LOG.info("Sync of $flakeRoot failed", e)
            // TODO: Map well-known nix errors (missing `flake.lock`, syntax errors with position, untracked files in git)
            //  to BuildIssue/FileMessageEvent, so the Sync view can navigate to `flake.nix:line:column`.
            root.message("Import failed", e.message ?: e.toString(), MessageEvent.Kind.ERROR, null)
            root.fail()
            if (!explicit) {
                // TODO(decision): Show a notification for failed auto-reloads, or rely on the Sync view only?
            }
        }
        finally {
            publisher.syncFinished(flakeRoot, success)
        }
    }

    private suspend fun apply(flake: NixFlakeImportState.FlakeState) {
        val state = NixFlakeImportState.getInstance(project)
        val oldRoots = inputRoots(state.getFlake(flake.root!!))
        state.update(flake)
        NixFlakeWorkspaceModel.applyFlake(project, flake)
        NixFlakeInputsRootsProvider.notifyInputsChanged(project, oldRoots, inputRoots(flake))
    }

    private fun inputRoots(flake: NixFlakeImportState.FlakeState?): List<VirtualFile> =
        flake?.inputs.orEmpty().mapNotNull { input -> input.storePath?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it) } }

    /** `nix flake archive --json` returns `{"path": …, "inputs": {"<name>": {"path": …, "inputs": {…}}}}`. */
    private fun collectInputs(node: JsonObject, prefix: String, into: MutableList<NixFlakeImportState.InputState>) {
        val inputs = node.getAsJsonObject("inputs") ?: return
        for ((name, value) in inputs.entrySet()) {
            val input = value.asJsonObject
            val fullName = prefix + name
            into.add(NixFlakeImportState.InputState().apply {
                this.name = fullName
                this.storePath = input.str("path")
            })
            // TODO(decision): Include transitive inputs (e.g. `home-manager/nixpkgs`)? They are often duplicates via `follows`.
            collectInputs(input, "$fullName/", into)
        }
    }

    private suspend fun step(
        parent: BuildProgress<BuildProgressDescriptor>,
        title: String,
        body: suspend (BuildProgress<BuildProgressDescriptor>) -> Unit,
    ) {
        val child = parent.startChildProgress(title)
        try {
            body(child)
            child.finish()
        }
        catch (e: CancellationException) {
            child.cancel()
            throw e
        }
        catch (e: Exception) {
            child.fail()
            throw e
        }
    }

    private fun descriptor(buildDescriptor: DefaultBuildDescriptor) = object : BuildProgressDescriptor {
        override fun getTitle(): String = buildDescriptor.title
        override fun getBuildDescriptor() = buildDescriptor
    }

    private fun parse(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun JsonObject.str(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

    /** Used by [NixFlakeProjectAware] to report sync start/end to the auto-import tracker, and by the tool window. */
    interface Listener {
        fun syncStarted(flakeRoot: String) {}
        fun syncFinished(flakeRoot: String, success: Boolean) {}
    }

    companion object {
        private val LOG = logger<NixFlakeSyncService>()

        @JvmField
        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic(Listener::class.java, Topic.BroadcastDirection.NONE)

        @JvmStatic
        fun getInstance(project: Project): NixFlakeSyncService = project.service()
    }
}
