package org.nixos.idea.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.lang.references.NixSymbolDeclaration;
import org.nixos.idea.lang.references.symbol.NixBuiltinSymbol;
import org.nixos.idea.lang.references.symbol.NixUserSymbol;
import org.nixos.idea.psi.NixDeclarationHost;
import org.nixos.idea.psi.NixPsiUtil;

public final class NixShadowedBuiltinInspection extends NixInspection<NixDeclarationHost> {

    public NixShadowedBuiltinInspection() {
        super(NixDeclarationHost.class);
    }

    @Override
    protected void check(@NotNull NixDeclarationHost host, @NotNull ProblemsHolder holder) {
        for (NixUserSymbol variable : host.getDeclaredVariables()) {
            boolean shadowsBuiltin = NixPsiUtil.getOuterScope(host).resolveVariable(variable.getName())
                    .stream().anyMatch(NixBuiltinSymbol.class::isInstance);
            if (shadowsBuiltin) {
                for (NixSymbolDeclaration declaration : variable.getDeclarations()) {
                    holder.problem(declaration.getIdentifier(), "Variable '" + variable.getName() + "' shadows a built-in").register();
                }
            }
        }
    }
}
