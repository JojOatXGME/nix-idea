package org.nixos.idea.flake.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/** Lets the user pick the linked flakes to unlink. Used by [NixFlakeUnlinkAction]. */
internal class NixFlakeUnlinkDialog(
    private val project: Project,
    private val linkedFlakeRoots: List<String>,
    preselected: Set<String>,
) : DialogWrapper(project) {

    private val checked = linkedFlakeRoots.associateWith { it in preselected }.toMutableMap()

    val selectedFlakeRoots: List<String>
        get() = linkedFlakeRoots.filter { checked[it] == true }

    init {
        title = "Unlink Nix Flake"
        setOKButtonText("Unlink")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row { label("Select the flakes to unlink. Their modules and imported state are removed from the project.") }
        for (root in linkedFlakeRoots) {
            row {
                checkBox(presentablePath(root)).bindSelected({ checked[root] == true }, { checked[root] = it })
            }
        }
        // TODO: Disable "Unlink" while nothing is checked.
    }

    private fun presentablePath(root: String): String {
        val base = project.basePath ?: return root
        val relative = FileUtil.getRelativePath(base, root, '/') ?: return root
        return if (relative == ".") root else relative
    }
}
