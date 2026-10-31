package org.nixos.idea.flake.ui

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.nixos.idea.flake.model.NixFlakeImportState
import org.nixos.idea.icon.NixIcons
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/** Content of the tool window created by [NixFlakeToolWindowFactory]. */
internal class NixFlakeToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable, UiDataProvider {

    private val root = DefaultMutableTreeNode()
    private val model = DefaultTreeModel(root)
    private val tree = Tree(model)

    init {
        tree.isRootVisible = false
        tree.cellRenderer = Renderer()
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) openSelected()
            }
        })
        setContent(ScrollPaneFactory.createScrollPane(tree))

        val group = ActionManager.getInstance().getAction("NixFlake.ToolWindow.Toolbar") as ActionGroup
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, group, true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)

        project.messageBus.connect(this).subscribe(NixFlakeImportState.TOPIC, NixFlakeImportState.Listener {
            ApplicationManager.getApplication().invokeLater(::rebuild, project.disposed)
        })
        rebuild()
    }

    private fun rebuild() {
        root.removeAllChildren()
        for (flake in NixFlakeImportState.getInstance(project).flakes) {
            val flakeNode = DefaultMutableTreeNode(Item.Flake(flake.root.orEmpty(), flake.description))
            val inputsNode = DefaultMutableTreeNode(Item.Group("Inputs"))
            flake.inputs.forEach { inputsNode.add(DefaultMutableTreeNode(Item.Input(it.name.orEmpty(), it.storePath, it.lockedRef))) }
            flakeNode.add(inputsNode)
            flake.formatter?.let { flakeNode.add(DefaultMutableTreeNode(Item.Formatter(it))) }
            root.add(flakeNode)
        }
        model.reload()
        TreeUtil.expand(tree, 2)
    }

    /** Selects the input's sources in the Project view (under "External Libraries"). */
    private fun openSelected() {
        val item = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? Item.Input ?: return
        val file = item.storePath?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return
        ProjectView.getInstance(project).select(null, file, true)
    }

    /** Provides the flake of the selected node to actions in the toolbar, e.g. [NixFlakeUnlinkAction]. */
    override fun uiDataSnapshot(sink: DataSink) {
        sink[SELECTED_FLAKE_ROOT] = selectedFlakeRoot()
    }

    private fun selectedFlakeRoot(): String? {
        var node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        while (node != null) {
            (node.userObject as? Item.Flake)?.let { return it.root }
            node = node.parent as? DefaultMutableTreeNode
        }
        return null
    }

    override fun dispose() {}

    sealed interface Item {
        data class Flake(val root: String, val description: String?) : Item
        data class Group(val title: String) : Item
        data class Input(val name: String, val storePath: String?, val lockedRef: String?) : Item
        data class Formatter(val outPath: String) : Item
    }

    private class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean,
        ) {
            when (val item = (value as? DefaultMutableTreeNode)?.userObject) {
                is Item.Flake -> {
                    icon = NixIcons.FILE
                    append(item.root.substringAfterLast('/'))
                    item.description?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                }
                is Item.Group -> append(item.title)
                is Item.Input -> {
                    append(item.name)
                    item.lockedRef?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                }
                is Item.Formatter -> {
                    append("Formatter")
                    append("  ${item.outPath}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }
    }

    companion object {
        /** Root directory of the flake selected in the tool window, if any. */
        @JvmField
        val SELECTED_FLAKE_ROOT: DataKey<String> = DataKey.create("NixFlake.SelectedFlakeRoot")
    }
}
