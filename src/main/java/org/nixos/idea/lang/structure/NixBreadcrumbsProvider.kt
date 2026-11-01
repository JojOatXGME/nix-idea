package org.nixos.idea.lang.structure

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.nixos.idea.lang.NixLanguage
import org.nixos.idea.psi.NixBindAttr

/**
 * Shows the attribute paths of the enclosing bindings as breadcrumbs.
 */
class NixBreadcrumbsProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(NixLanguage.INSTANCE)

    override fun acceptElement(element: PsiElement): Boolean = element is NixBindAttr

    override fun getElementInfo(element: PsiElement): String = (element as NixBindAttr).attrPath.text

    override fun getElementTooltip(element: PsiElement): String? = null
}
