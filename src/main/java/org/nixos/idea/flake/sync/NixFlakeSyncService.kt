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
import com.intellij.util.ThreeState
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nixos.idea.flake.NixFlakeActivityKey
import org.nixos.idea.flake.NixFlakeGcRoots
import org.nixos.idea.flake.NixLinkedFlakesSettings
import org.nixos.idea.flake.model.NixFlakeImportState
import org.nixos.idea.flake.model.NixFlakeInputsRootsProvider
import org.nixos.idea.flake.model.NixFlakeWorkspaceModel
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

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
 *  3. Build formatter: `nix build .#formatter.<system>` (only if `nix fmt` support is enabled)
 *  4. Apply the result to [NixFlakeImportState], the Workspace Model, and the library roots.
 *
 * Flakes are referenced by their plain directory (not `path:<dir>`), so nix behaves like `nix flake` on the command
 * line: inside a git repository, files which are not tracked by git are ignored.
 *
 * Why a service and not static methods: the service gets a [CoroutineScope] from the platform which is cancelled when
 * the project is closed or the plugin is unloaded, so running syncs don't outlive their project. It is also created
 * lazily, and tests can replace it via `ServiceContainerUtil.replaceService`.
 */
@Service(Service.Level.PROJECT)
class NixFlakeSyncService(private val project: Project, private val cs: CoroutineScope) {

    /** One lock per flake: different flakes are imported in parallel (each has its own node in the Sync view). */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Starts a sync in the background and returns immediately. Used by actions and the auto-import tracker. */
    fun scheduleSync(flakeRoot: String, explicit: Boolean) {
        cs.launch { sync(flakeRoot, explicit) }
    }

    fun scheduleSyncAll(explicit: Boolean) {
        for (root in NixLinkedFlakesSettings.getInstance(project).linkedFlakes) {
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
        // TODO: A sync which is requested while another sync of the same flake is running waits for the running one.
        //  It could instead cancel the running sync (the files changed again), or be dropped if one is already queued.
        project.trackActivity(NixFlakeActivityKey) {
            locks.computeIfAbsent(flakeRoot) { Mutex() }.withLock {
                withBackgroundProgress(project, "Importing Nix flake ${Path.of(flakeRoot).fileName}") {
                    doSync(flakeRoot, explicit)
                }
            }
        }
    }

    private suspend fun doSync(flakeRoot: String, explicit: Boolean) {
        val publisher = project.messageBus.syncPublisher(TOPIC)
        publisher.syncStarted(flakeRoot)
        var success = false

        val buildDescriptor = DefaultBuildDescriptor(Any(), "Nix flake ${Path.of(flakeRoot).fileName}", flakeRoot, System.currentTimeMillis())
            .withRestartAction(ActionManager.getInstance().getAction("nix-idea.flake.reload"))
        // Same behavior as the Gradle and Maven integrations (see `ProjectAware.reloadProject` and
        // `ExternalSystemUtil` in intellij-community): The tool window is never activated when a sync starts; the
        // progress bar in the status bar is the main indicator. When an explicit sync fails, the Build tool window is
        // activated and shows the error. Failures of automatic reloads only show up in the Sync tab.
        buildDescriptor.setActivateToolWindowWhenAdded(false)
        buildDescriptor.setActivateToolWindowWhenFailed(explicit)
        buildDescriptor.setAutoFocusContent(explicit)
        buildDescriptor.setNavigateToError(if (explicit) ThreeState.UNSURE else ThreeState.NO)
        val root = SyncViewManager.createBuildProgress(project).start(descriptor(buildDescriptor))
        try {
            val dir = Path.of(flakeRoot)
            val flake = NixFlakeImportState.FlakeState().apply { this.root = flakeRoot }

            step(root, "Evaluate flake") { step ->
                val metadata = parse(NixCommand.run(step, dir, "flake", "metadata", "--json", flakeRoot))
                flake.description = metadata.str("description")
                flake.storePath = metadata.str("path")
                flake.lastModified = metadata.get("lastModified")?.asLong ?: 0L
                // TODO: `metadata.locks.nodes` has the locked refs and `flake = false` markers of all inputs.
                //  Merge them into the inputs collected in the next step (InputState.lockedRef, InputState.isFlake).
            }

            step(root, "Fetch inputs") { step ->
                val archive = parse(NixCommand.run(step, dir, "flake", "archive", "--json", flakeRoot))
                flake.inputs = collectInputs(archive)
            }

            if (isNixFmtEnabled()) {
                step(root, "Build formatter") { step ->
                    // Part of the import, because IntelliJ's external formatter integration times out after 30 seconds,
                    // which is not enough to build the formatter on first use. Most users will format at some point anyway.
                    // TODO: Feed the out path into the `nix fmt` integration (https://github.com/NixOS/nix-idea/pull/104).
                    val system = NixCommand.run(step, dir, "eval", "--impure", "--raw", "--expr", "builtins.currentSystem").trim()
                    val hasFormatter = NixCommand.run(
                        step, dir, "eval", "--json", "$flakeRoot#formatter", "--apply", "f: f ? \"$system\"",
                    ).trim() == "true"
                    if (hasFormatter) {
                        // The out-link is a GC root, so `nix-collect-garbage` doesn't delete the formatter.
                        val outLink = NixFlakeGcRoots.outLink(project, flakeRoot, "formatter")
                        flake.formatter = NixCommand.run(
                            step, dir, "build", "--out-link", outLink.toString(), "--print-out-paths", "$flakeRoot#formatter.$system",
                        ).trim().lines().firstOrNull()
                    }
                    else {
                        step.output("Flake has no formatter for $system\n", true)
                    }
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
            // TODO: Use the error mapping of NixCommand (BuildIssue/FileMessageEvent with file and position) once implemented.
            root.message("Import failed", e.message ?: e.toString(), MessageEvent.Kind.ERROR, null)
            root.fail()
        }
        finally {
            publisher.syncFinished(flakeRoot, success)
        }
    }

    /** TODO: Return the setting of the `nix fmt` integration once https://github.com/NixOS/nix-idea/pull/104 is merged. */
    private fun isNixFmtEnabled(): Boolean = false

    private suspend fun apply(flake: NixFlakeImportState.FlakeState) {
        val state = NixFlakeImportState.getInstance(project)
        val oldRoots = inputRoots(state.getFlake(flake.root!!))
        state.update(flake)
        NixFlakeWorkspaceModel.applyFlake(project, flake)
        NixFlakeInputsRootsProvider.notifyInputsChanged(project, oldRoots, inputRoots(flake))
    }

    private fun inputRoots(flake: NixFlakeImportState.FlakeState?): List<VirtualFile> =
        flake?.inputs.orEmpty().mapNotNull { input -> input.storePath?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it) } }

    /**
     * `nix flake archive --json` returns `{"path": …, "inputs": {"<name>": {"path": …, "inputs": {…}}}}`.
     *
     * Includes transitive inputs (e.g. `home-manager/nixpkgs`), so their sources are browsable as well.
     * Inputs are deduplicated by store path (`follows` and identical locks result in the same path). The tree is
     * traversed breadth-first, so an input which is both direct and transitive keeps the name of the direct input.
     */
    private fun collectInputs(archive: JsonObject): MutableList<NixFlakeImportState.InputState> {
        val result = LinkedHashMap<String, NixFlakeImportState.InputState>()
        val queue = ArrayDeque<Pair<String, JsonObject>>()
        queue.add("" to archive)
        while (queue.isNotEmpty()) {
            val (prefix, node) = queue.removeFirst()
            val inputs = node.getAsJsonObject("inputs") ?: continue
            for ((name, value) in inputs.entrySet()) {
                val input = value.asJsonObject
                val fullName = prefix + name
                val storePath = input.str("path") ?: continue
                result.getOrPut(storePath) {
                    NixFlakeImportState.InputState().apply {
                        this.name = fullName
                        this.storePath = storePath
                        this.direct = prefix.isEmpty()
                    }
                }
                queue.add("$fullName/" to input)
            }
        }
        return result.values.toMutableList()
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
