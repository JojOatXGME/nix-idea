package org.nixos.idea.flake.build

import com.intellij.openapi.project.Project
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.nixos.idea.flake.model.NixFlakeWorkspaceModel

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
        return projectTask is ModuleBuildTask && NixFlakeWorkspaceModel.getFlakeRoot(projectTask.module) != null
    }

    override fun run(project: Project, context: ProjectTaskContext, vararg tasks: ProjectTask): Promise<Result> {
        val promise = AsyncPromise<Result>()
        // The module has no content root, so the flake is looked up via the module's external system options.
        val roots = tasks.filterIsInstance<ModuleBuildTask>()
            .mapNotNull { NixFlakeWorkspaceModel.getFlakeRoot(it.module) }
            .distinct()
        NixFlakeBuildService.getInstance(project).build(roots, promise)
        return promise
    }
}
