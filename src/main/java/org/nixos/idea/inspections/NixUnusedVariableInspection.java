package org.nixos.idea.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.lang.references.NixSymbolDeclaration;
import org.nixos.idea.lang.references.NixSymbolUtil;
import org.nixos.idea.lang.references.symbol.NixUserSymbol;
import org.nixos.idea.psi.NixDeclarationHost;
import org.nixos.idea.psi.NixExprAttrs;
import org.nixos.idea.psi.NixFormal;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class NixUnusedVariableInspection extends NixInspection<NixDeclarationHost> {

    @SuppressWarnings("PublicField") // Required for options of inspections
    public boolean reportFormals = false;

    public NixUnusedVariableInspection() {
        super(NixDeclarationHost.class);
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
        return pane(checkbox("reportFormals", "Report unused attributes in function arguments (e.g. 'x' in '{ x }: ...')"));
    }

    @Override
    protected void check(@NotNull NixDeclarationHost host, @NotNull ProblemsHolder holder) {
        if (host instanceof NixExprAttrs) {
            return; // Attributes of recursive sets are used as part of the result (even for legacy let-expressions)
        }
        for (NixUserSymbol variable : host.getDeclaredVariables()) {
            if (variable.getName().startsWith("_") || NixSymbolUtil.isReferenced(variable)) {
                continue;
            }
            for (NixSymbolDeclaration declaration : variable.getDeclarations()) {
                if (declaration.getDeclaringElement() instanceof NixFormal && !reportFormals) {
                    continue;
                }
                holder.problem(declaration.getIdentifier(), "Unused variable '" + variable.getName() + "'")
                        .highlight(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                        .maybeFix(NixDeleteFix.declaration(declaration, "Remove variable"))
                        .register();
            }
        }
    }
}
