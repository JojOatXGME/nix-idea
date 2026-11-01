package org.nixos.idea.lang.references;

import org.jetbrains.annotations.NotNull;
import org.nixos.idea.lang.builtins.NixBuiltin;
import org.nixos.idea.lang.references.symbol.NixSymbol;
import org.nixos.idea.psi.NixExprVar;
import org.nixos.idea.psi.NixPsiElement;

import java.util.Collection;
import java.util.List;

/**
 * Reference to a builtin accessed via the {@code builtins} attribute set, e.g. {@code builtins.map}.
 * The reference only resolves if {@code builtins} refers to the builtin of the same name, i.e. is not shadowed.
 */
@SuppressWarnings("UnstableApiUsage")
public final class NixBuiltinAttributeReference extends NixSymbolReference {

    private static final String BUILTINS = "builtins";

    private final @NotNull NixExprVar myBuiltinsVar;
    private final @NotNull NixBuiltin myBuiltin;

    private NixBuiltinAttributeReference(@NotNull NixPsiElement element, @NotNull NixPsiElement identifier,
                                         @NotNull NixExprVar builtinsVar, @NotNull NixBuiltin builtin) {
        super(element, identifier, builtin.name());
        myBuiltinsVar = builtinsVar;
        myBuiltin = builtin;
    }

    /**
     * Creates a reference if the given expression looks like an access to a builtin.
     *
     * @param element      The element owning the reference.
     * @param identifier   The attribute naming the builtin.
     * @param source       The expression of which the attribute is accessed.
     * @param attributeName The name of the accessed attribute.
     * @return The reference, or an empty list if the attribute access does not look like an access to a builtin.
     */
    public static @NotNull List<NixBuiltinAttributeReference> create(@NotNull NixPsiElement element,
                                                                     @NotNull NixPsiElement identifier,
                                                                     @NotNull NixPsiElement source,
                                                                     @NotNull String attributeName) {
        if (source instanceof NixExprVar builtinsVar && BUILTINS.equals(builtinsVar.getText())) {
            NixBuiltin builtin = NixBuiltin.resolveBuiltin(attributeName);
            if (builtin != null) {
                return List.of(new NixBuiltinAttributeReference(element, identifier, builtinsVar, builtin));
            }
        }
        return List.of();
    }

    @Override
    public @NotNull Collection<NixSymbol> resolveReference() {
        for (NixSymbol symbol : myBuiltinsVar.getScope().resolveVariable(BUILTINS)) {
            NixBuiltin builtin = symbol.getBuiltin();
            if (builtin != null && BUILTINS.equals(builtin.name())) {
                return List.of(NixSymbol.builtin(myBuiltin));
            }
        }
        return List.of();
    }
}
