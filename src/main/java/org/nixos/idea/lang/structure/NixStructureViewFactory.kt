package org.nixos.idea.lang.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.nixos.idea.psi.NixAttr
import org.nixos.idea.psi.NixBindAttr
import org.nixos.idea.psi.NixBindInherit

class NixStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder {
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                NixStructureViewModel(psiFile, editor)
        }
    }
}

class NixStructureViewModel(file: PsiFile, editor: Editor?) :
    StructureViewModelBase(file, editor, NixStructureViewElement(file)),
    StructureViewModel.ElementInfoProvider {

    init {
        withSorters(Sorter.ALPHA_SORTER)
    }

    override fun isSuitable(element: PsiElement?): Boolean =
        element is NixBindAttr || (element is NixAttr && element.parent is NixBindInherit)

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
        element.value is NixAttr
}
