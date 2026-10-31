package org.nixos.idea.flake.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.nixos.idea.flake.NixFlakeSettings
import org.nixos.idea.flake.sync.NixFlakeSyncService

/**
 * Re-imports all linked flakes. Also used as restart action in the Sync view.
 *
 * Intentionally ignores the selection in the tool window: it is too easy to not notice that a flake is selected.
 * Reloading a single flake could later be offered on the flake's node (context menu or inline button).
 */
class NixFlakeReloadAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && NixFlakeSettings.getInstance(project).linkedFlakes.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        NixFlakeSyncService.getInstance(project).scheduleSyncAll(explicit = true)
    }
}
