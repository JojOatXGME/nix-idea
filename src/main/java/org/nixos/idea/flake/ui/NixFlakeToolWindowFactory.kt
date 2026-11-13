package org.nixos.idea.flake.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.nixos.idea.flake.NixFlakeProjectSettings

/**
 * Tool window on the right side showing the linked flakes, their inputs, and the formatter.
 *
 * Gradle's tool window (`GradleToolWindowFactory` → `ExternalProjectsViewImpl`) renders the cached DataNode tree of the
 * classic External System integration and can't be reused without implementing `ExternalSystemManager`.
 * Maven builds its own tool window (`MavenProjectsNavigator`), which is what this class does in a minimal way.
 *
 * TODO(decision): Content of the tree. Ideas: flake outputs (`nix flake show --json`: packages, devShells, checks, apps)
 *  with actions to build or run them; inputs with "Update input" (`nix flake update <input>`); last sync time/status.
 * TODO: Use `StructureTreeModel` + `AsyncTreeModel` once the tree gets bigger, to compute nodes off the EDT.
 */
class NixFlakeToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun shouldBeAvailable(project: Project): Boolean =
        // TODO(decision): Also show for projects with an unlinked flake.nix, to offer "Link flake" from the tool window?
        NixFlakeProjectSettings.getInstance(project).linkedFlakes.isNotEmpty()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = NixFlakeToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
