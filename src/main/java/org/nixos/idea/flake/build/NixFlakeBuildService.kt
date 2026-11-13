package org.nixos.idea.flake.build

import com.google.gson.JsonParser
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.TaskRunnerResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.AsyncPromise
import org.nixos.idea.flake.NixFlakeGcRoots
import org.nixos.idea.flake.sync.NixCommand
import java.nio.file.Path

/**
 * Builds linked flakes and reports the progress in the "Build" tab of the Build tool window.
 *
 * "Build Project" builds all packages of the flake for the current system (`packages.<system>.*`). This matches
 * "Build Project" in Java projects, which compiles everything but doesn't run tests (so no `checks`).
 *
 * The results are not linked as `result` symlinks into the project directory. Instead, `--out-link` points into the
 * IDE system directory, which keeps the results alive as GC roots without touching the project, see [NixFlakeGcRoots].
 */
@Service(Service.Level.PROJECT)
internal class NixFlakeBuildService(private val project: Project, private val cs: CoroutineScope) {

    fun build(flakeRoots: List<String>, promise: AsyncPromise<ProjectTaskRunner.Result>) {
        cs.launch {
            val descriptor = DefaultBuildDescriptor(Any(), "Nix build", project.basePath.orEmpty(), System.currentTimeMillis())
            val root = BuildViewManager.createBuildProgress(project).start(object : BuildProgressDescriptor {
                override fun getTitle() = descriptor.title
                override fun getBuildDescriptor() = descriptor
            })
            try {
                for (flakeRoot in flakeRoots) {
                    val step = root.startChildProgress("nix build ${Path.of(flakeRoot).fileName}")
                    try {
                        val dir = Path.of(flakeRoot)
                        // TODO: Share the system with the sync (store it in NixFlakeImportState.FlakeState).
                        val system = NixCommand.run(step, dir, "eval", "--impure", "--raw", "--expr", "builtins.currentSystem").trim()
                        val packagesAttr = "$flakeRoot#packages.$system"
                        val names = JsonParser.parseString(
                            NixCommand.run(step, dir, "eval", "--json", packagesAttr, "--apply", "p: builtins.attrNames p"),
                        ).asJsonArray.map { it.asString }
                        // TODO: A flake without `packages.<system>` makes the eval above fail. Treat it as "nothing to build".
                        if (names.isEmpty()) {
                            step.output("Flake has no packages for $system\n", true)
                        }
                        else {
                            // With multiple installables, nix names the links `packages`, `packages-1`, `packages-2`, …
                            // TODO: Remove stale links when packages are removed from the flake.
                            // TODO(decision): "Rebuild Project" (`ModuleBuildTask.isIncrementalBuild == false`) has no
                            //  real equivalent. `--rebuild` checks reproducibility instead of rebuilding from scratch.
                            val outLink = NixFlakeGcRoots.outLink(project, flakeRoot, "packages")
                            NixCommand.run(
                                step, dir, "build", "--print-build-logs", "--out-link", outLink.toString(),
                                // Quote the names, as package names may contain dots.
                                *names.map { "$packagesAttr.\"$it\"" }.toTypedArray(),
                            )
                        }
                        step.finish()
                    }
                    catch (e: CancellationException) {
                        step.cancel()
                        throw e
                    }
                    catch (e: Exception) {
                        step.fail()
                        throw e
                    }
                }
                root.finish()
                promise.setResult(TaskRunnerResults.SUCCESS)
            }
            catch (e: CancellationException) {
                root.cancel()
                promise.setResult(TaskRunnerResults.ABORTED)
                throw e
            }
            catch (e: Exception) {
                root.fail()
                promise.setResult(TaskRunnerResults.FAILURE)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): NixFlakeBuildService = project.service()
    }
}
