package org.nixos.idea.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.psi.NixExprUri;
import org.nixos.idea.util.NixStringUtil;

public final class NixUnquotedUriInspection extends NixInspection<NixExprUri> {

    public NixUnquotedUriInspection() {
        super(NixExprUri.class);
    }

    @Override
    protected void check(@NotNull NixExprUri uri, @NotNull ProblemsHolder holder) {
        holder.problem(uri, "Unquoted URLs are deprecated")
                .fix(new NixReplaceFix(uri, NixStringUtil.quote(uri.getText()), "Quote URL"))
                .register();
    }
}
