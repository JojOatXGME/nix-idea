package org.nixos.idea.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.psi.NixExpr;
import org.nixos.idea.psi.NixExprParens;
import org.nixos.idea.psi.NixPsiUtil;

public final class NixRedundantParenthesesInspection extends NixInspection<NixExprParens> {

    public NixRedundantParenthesesInspection() {
        super(NixExprParens.class);
    }

    @Override
    protected void check(@NotNull NixExprParens parens, @NotNull ProblemsHolder holder) {
        NixExpr inner = parens.getExpr();
        if (inner != null &&
                PsiTreeUtil.getChildOfType(parens, PsiComment.class) == null &&
                NixPsiUtil.canReplaceWithoutParentheses(parens, inner)) {
            holder.problem(parens, "Redundant parentheses")
                    .highlight(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                    .fix(new NixReplaceFix(parens, inner.getText(), "Remove redundant parentheses"))
                    .register();
        }
    }
}
