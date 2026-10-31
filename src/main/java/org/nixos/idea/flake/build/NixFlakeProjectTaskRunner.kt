package org.nixos.idea.flake.build

import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.TaskRunnerResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.nixos.idea.flake.model.NixFlakeWorkspaceModel
import org.nixos.idea.flake.sync.NixCommand
import java.nio.file.Path

/**
 * Makes "Build | Build Project" (and "Build Module") run `nix build` for modules created by the flake import.
 *
 * "Build Project" calls `ProjectTaskManager.buildAllModules()`, which creates one [ModuleBuildTask] per module and asks
 * every registered [ProjectTaskRunner] whether it [canRun] the task. Reference: `GradleProjectTaskRunner`, `MavenProjectTaskRunner`.
 *
 * Note: The "Build" menu is mostly an IntelliJ IDEA feature. Other IDEs may not show it, so the tool window should also
 * offer a "Build flake" action.
 */
class NixFlakeProjectTaskRunner : ProjectTaskRunner() {

    // Still abstract in 2025.2, but obsolete and never called by the platform. Newer platform versions removed the abstractness.
    @Deprecated("Obsolete since 2023.3")
    override fun canRun(projectTask: ProjectTask): Boolean = false

    override fun canRun(project: Project, projectTask: ProjectTask, context: ProjectTaskContext?): Boolean {
        if (projectTask !is ModuleBuildTask) return false
        val entity = project.workspaceModel.currentSnapshot.resolve(ModuleId(projectTask.module.name)) ?: return false
        return NixFlakeWorkspaceModel.isNixFlakeModule(entity)
    }

    override fun run(project: Project, context: ProjectTaskContext, vararg tasks: ProjectTask): Promise<Result> {
        val promise = AsyncPromise<Result>()
        val roots = tasks.filterIsInstance<ModuleBuildTask>().mapNotNull { task ->
            // The content root of the module is the flake directory, see NixFlakeWorkspaceModel.applyFlake.
            project.workspaceModel.currentSnapshot.resolve(ModuleId(task.module.name))
                ?.contentRoots?.firstOrNull()?.url?.url?.let(VfsUtilCore::urlToPath)
        }
        project.service<NixFlakeBuildService>().build(roots, promise)
        return promise
    }
}

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
                        // TODO(decision): What does "Build Project" build? Options: `packages.<system>.default` (`nix build`),
                        //  all packages, `nix flake check` (builds checks), or a per-flake setting. "Rebuild Project"
                        //  (`ModuleBuildTask.isIncrementalBuild == false`) could add `--rebuild`.
                        // TODO(decision): Keep or drop the `result` symlink? (`--no-link`, `--out-link`)
                        NixCommand.run(step, Path.of(flakeRoot), "build", "path:$flakeRoot", "--print-build-logs")
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
}
