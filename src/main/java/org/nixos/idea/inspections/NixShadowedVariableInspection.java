package org.nixos.idea.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.lang.references.NixSymbolDeclaration;
import org.nixos.idea.lang.references.symbol.NixUserSymbol;
import org.nixos.idea.psi.NixDeclarationHost;
import org.nixos.idea.psi.NixPsiUtil;

public final class NixShadowedVariableInspection extends NixInspection<NixDeclarationHost> {

    public NixShadowedVariableInspection() {
        super(NixDeclarationHost.class);
    }

    @Override
    protected void check(@NotNull NixDeclarationHost host, @NotNull ProblemsHolder holder) {
        for (NixUserSymbol variable : host.getDeclaredVariables()) {
            boolean shadowsVariable = NixPsiUtil.getOuterScope(host).resolveVariable(variable.getName())
                    .stream().anyMatch(NixUserSymbol.class::isInstance);
            if (shadowsVariable) {
                for (NixSymbolDeclaration declaration : variable.getDeclarations()) {
                    holder.problem(declaration.getIdentifier(), "Variable '" + variable.getName() + "' shadows another variable").register();
                }
            }
        }
    }
}
