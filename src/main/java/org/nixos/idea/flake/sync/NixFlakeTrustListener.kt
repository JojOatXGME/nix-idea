package org.nixos.idea.flake.sync

import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.project.Project

/**
 * Re-runs the import when the user trusts the project, since [NixFlakeSyncService] skips untrusted projects.
 * Registered as an application listener in plugin.xml. Reference: `ExternalSystemTrustedListener`.
 */
internal class NixFlakeTrustListener : TrustedProjectsListener {
    override fun onProjectTrusted(project: Project) {
        // TODO(decision): Only re-sync flakes that were never synced, or all of them?
        NixFlakeSyncService.getInstance(project).scheduleSyncAll(explicit = false)
    }
}
