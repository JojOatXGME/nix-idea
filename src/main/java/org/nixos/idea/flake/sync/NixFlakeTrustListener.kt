package org.nixos.idea.flake.sync

import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.project.Project
import org.nixos.idea.settings.NixFlakeSettings

/**
 * Re-runs the import when the user trusts the project, since [NixFlakeSyncService] skips untrusted projects.
 * Only if [NixFlakeSettings.autoImport] is enabled; otherwise, the user reloads explicitly.
 * Registered as an application listener in plugin.xml. Reference: `ExternalSystemTrustedListener`.
 */
internal class NixFlakeTrustListener : TrustedProjectsListener {
    override fun onProjectTrusted(project: Project) {
        if (!NixFlakeSettings.getInstance().autoImport) return
        // TODO(decision): Only re-sync flakes that were never synced, or all of them?
        NixFlakeSyncService.getInstance(project).scheduleSyncAll(explicit = false)
    }
}
