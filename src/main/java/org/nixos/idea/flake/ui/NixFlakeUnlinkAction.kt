package org.nixos.idea.flake.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import org.nixos.idea.flake.NixFlakeConstants
import org.nixos.idea.flake.NixFlakeSettings

/**
 * "Unlink Nix Flake…": asks which flakes to unlink, with the flake selected in the tool window preselected.
 *
 * The same dialog is used whether or not a flake is selected (e.g. when invoked via *Find Action*), so the user always
 * sees what is going to be unlinked.
 */
class NixFlakeUnlinkAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && NixFlakeSettings.getInstance(project).linkedFlakes.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val linked = NixFlakeSettings.getInstance(project).linkedFlakes
        val selected = e.getData(NixFlakeToolWindowPanel.SELECTED_FLAKE_ROOT)
        val dialog = NixFlakeUnlinkDialog(project, linked, preselected = setOfNotNull(selected))
        if (!dialog.showAndGet()) return
        val roots = dialog.selectedFlakeRoots
        if (roots.isEmpty()) return

        val aware = ExternalSystemUnlinkedProjectAware.getInstance(NixFlakeConstants.SYSTEM_ID) ?: return
        runWithModalProgressBlocking(project, "Unlinking Nix flakes") {
            for (root in roots) {
                aware.unlinkProject(project, root)
            }
        }
    }
}
