package org.nixos.idea.lang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.nixos.idea.psi.NixAttr
import org.nixos.idea.psi.NixBindAttr
import org.nixos.idea.psi.NixBindInherit
import org.nixos.idea.psi.NixExpr
import org.nixos.idea.psi.NixExprApp
import org.nixos.idea.psi.NixExprAssert
import org.nixos.idea.psi.NixExprAttrs
import org.nixos.idea.psi.NixExprLambda
import org.nixos.idea.psi.NixExprLet
import org.nixos.idea.psi.NixExprOpUpdate
import org.nixos.idea.psi.NixExprParens
import org.nixos.idea.psi.NixExprWith
import org.nixos.idea.psi.NixPsiUtil
import org.nixos.idea.psi.NixBind
import javax.swing.Icon

/**
 * Element of the structure view.
 * The value is either the [PsiFile], a [NixBindAttr], or a [NixAttr] of a [NixBindInherit].
 * [isVariable] distinguishes bindings of `let` expressions from attributes of attribute sets.
 */
class NixStructureViewElement(
    element: PsiElement,
    private val isVariable: Boolean = false,
) : PsiTreeElementBase<PsiElement>(element) {

    override fun getPresentableText(): String? = when (val e = element) {
        is PsiFile -> e.name
        is NixBindAttr -> e.attrPath.text
        is NixAttr -> e.text
        else -> null
    }

    override fun getLocationString(): String? {
        val e = element
        if (e is NixAttr) {
            val source = (e.parent as? NixBindInherit)?.source ?: return "inherited"
            return "inherited from ${source.text}"
        }
        return null
    }

    override fun getIcon(open: Boolean): Icon? = when (element) {
        is PsiFile -> super.getIcon(open)
        else -> if (isVariable) AllIcons.Nodes.Variable else AllIcons.Nodes.Property
    }

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val result = mutableListOf<StructureViewTreeElement>()
        when (val e = element) {
            is PsiFile -> collect(PsiTreeUtil.getChildOfType(e, NixExpr::class.java), result)
            is NixBindAttr -> collect(e.expr, result)
        }
        return result
    }

    companion object {
        /**
         * Collects the bindings which are visible in the structure of the given expression.
         * Descends through expressions which commonly wrap attribute sets in Nix code,
         * like lambdas (`{ pkgs, ... }: { ... }`), function calls (`mkDerivation { ... }`),
         * `let ... in`, `with`, `assert`, parentheses, and `//`.
         */
        private fun collect(expr: PsiElement?, result: MutableList<StructureViewTreeElement>) {
            when (expr) {
                is NixExprAttrs -> addBindings(expr.bindList, NixPsiUtil.isLegacyLet(expr), result)
                is NixExprLet -> {
                    addBindings(expr.bindList, true, result)
                    collect(expr.expr, result)
                }
                is NixExprLambda -> collect(expr.expr, result)
                is NixExprParens -> collect(expr.expr, result)
                is NixExprWith -> collect(expr.exprList.getOrNull(1), result)
                is NixExprAssert -> collect(expr.exprList.getOrNull(1), result)
                // Operands are not necessarily NixExprOp, so we cannot use getExprOpList().
                is NixExprOpUpdate -> PsiTreeUtil.getChildrenOfTypeAsList(expr, NixExpr::class.java).forEach { collect(it, result) }
                is NixExprApp -> NixPsiUtil.getArguments(expr).forEach { collect(it, result) }
            }
        }

        private fun addBindings(
            binds: List<NixBind>,
            isVariable: Boolean,
            result: MutableList<StructureViewTreeElement>,
        ) {
            for (bind in binds) {
                when (bind) {
                    is NixBindAttr -> result.add(NixStructureViewElement(bind, isVariable))
                    is NixBindInherit -> bind.attributes.forEach { result.add(NixStructureViewElement(it, isVariable)) }
                }
            }
        }
    }
}
