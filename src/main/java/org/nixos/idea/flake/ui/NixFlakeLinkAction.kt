package org.nixos.idea.flake.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import org.nixos.idea.flake.NixFlakeConstants

/** Links a `flake.nix` the platform didn't suggest, e.g. one in a subdirectory. */
class NixFlakeLinkAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withFileFilter { it.name == NixFlakeConstants.FLAKE_FILE_NAME }
            .withTitle("Select flake.nix")
        val file = FileChooser.chooseFile(descriptor, project, null) ?: return
        val aware = ExternalSystemUnlinkedProjectAware.getInstance(NixFlakeConstants.SYSTEM_ID) ?: return
        // Goes through the trust dialog in NixFlakeUnlinkedProjectAware.linkAndLoadProjectAsync.
        // TODO: Avoid the modal progress; launch in the sync service's coroutine scope instead.
        runWithModalProgressBlocking(project, "Linking Nix flake") {
            aware.linkAndLoadProjectAsync(project, file.parent.path)
        }
    }
}
