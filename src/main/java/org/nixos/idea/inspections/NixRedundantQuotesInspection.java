package org.nixos.idea.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.psi.NixPsiUtil;
import org.nixos.idea.psi.NixStringAttr;
import org.nixos.idea.util.NixStringUtil;

public final class NixRedundantQuotesInspection extends NixInspection<NixStringAttr> {

    public NixRedundantQuotesInspection() {
        super(NixStringAttr.class);
    }

    @Override
    protected void check(@NotNull NixStringAttr attr, @NotNull ProblemsHolder holder) {
        String name = NixPsiUtil.getAttributeName(attr);
        if (attr.getStdString() != null && name != null && NixStringUtil.isIdentifier(name)) {
            holder.problem(attr, "Redundant quotes")
                    .highlight(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                    .fix(new NixReplaceFix(attr, name, "Remove quotes"))
                    .register();
        }
    }
}
