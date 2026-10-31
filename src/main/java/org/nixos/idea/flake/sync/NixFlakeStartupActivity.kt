package org.nixos.idea.flake.sync

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Starts [NixFlakeTrackerRegistrations] when a project is opened. */
internal class NixFlakeStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        NixFlakeTrackerRegistrations.getInstance(project).start()
    }
}
