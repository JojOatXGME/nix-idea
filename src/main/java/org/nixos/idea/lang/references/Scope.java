package org.nixos.idea.lang.references;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.lang.builtins.NixBuiltin;
import org.nixos.idea.lang.references.symbol.NixSymbol;
import org.nixos.idea.psi.NixDeclarationHost;
import org.nixos.idea.psi.NixExprWith;
import org.nixos.idea.psi.NixPsiElement;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scope used to lookup accessible variables.
 * This class is immutable, but it stores references to PSI elements.
 * If the underlying PSI elements change after the scope instance was created,
 * the scope instance may or may not reflect the changes.
 */
public abstract sealed class Scope {

    //region Factory methods

    @Contract(pure = true)
    public static @NotNull Scope root() {
        return Root.INSTANCE;
    }

    /**
     * Use {@link NixPsiElement#getScope()} instead of calling this method directly.
     */
    @Contract(pure = true)
    public static @NotNull Scope subScope(@NotNull Scope parent, @NotNull NixPsiElement element) {
        if (element instanceof NixExprWith withExpression) {
            return new With(parent, withExpression);
        } else if (element instanceof NixDeclarationHost declarationHost && declarationHost.isDeclaringVariables()) {
            return new LetOrRecursiveSet(parent, declarationHost);
        } else {
            return parent;
        }
    }

    //endregion
    //region Public API

    /**
     * Resolves the given variable name and returns matching symbols.
     *
     * @param variableName The name of the variable.
     * @return Symbols which may be references by the given variable.
     */
    @Contract(pure = true)
    public final @NotNull Collection<NixSymbol> resolveVariable(@NotNull String variableName) {
        NixSymbol result = resolveVariable0(variableName);
        return ContainerUtil.createMaybeSingletonList(result);
    }

    /**
     * Returns all variables which are accessible in this scope.
     * Variables declared in inner scopes shadow variables with the same name in outer scopes.
     * Variables of inner scopes come first in the iteration order of the returned map.
     *
     * @return Map from variable name to the symbol the variable resolves to.
     */
    @Contract(pure = true)
    public final @NotNull Map<String, NixSymbol> collectVariables() {
        Map<String, NixSymbol> result = new LinkedHashMap<>();
        collectVariables0(result);
        return result;
    }

    //endregion
    //region Abstract methods

    @Contract(pure = true)
    abstract @Nullable NixSymbol resolveVariable0(@NotNull String variableName);

    /**
     * Adds all variables of this scope to the given map, unless the map already contains a variable with the same name.
     *
     * @param result The map to which the variables shall be added.
     */
    abstract void collectVariables0(@NotNull Map<String, NixSymbol> result);

    //endregion
    //region Subclasses

    /**
     * Represents the scope at the root of a file.
     * The scope only contains built-ins provided by Nix itself.
     * The same instance may be reused for multiple files.
     */
    private static final class Root extends Scope {

        private static final Root INSTANCE = new Root();

        private Root() {} // Only called for Root.INSTANCE

        @Override
        public @Nullable NixSymbol resolveVariable0(@NotNull String variableName) {
            // TODO: Ideally, we should filter the built-ins based on the used version of Nix.
            NixBuiltin builtin = NixBuiltin.resolveGlobal(variableName);
            return builtin == null ? null : NixSymbol.builtin(builtin);
        }

        @Override
        void collectVariables0(@NotNull Map<String, NixSymbol> result) {
            for (NixBuiltin builtin : NixBuiltin.globals()) {
                result.putIfAbsent(builtin.name(), NixSymbol.builtin(builtin));
            }
        }
    }

    private static abstract sealed class Psi<T extends NixPsiElement> extends Scope {
        private final @NotNull Scope myParent;
        private final @NotNull T myElement;

        private Psi(@NotNull Scope parent, @NotNull T element) {
            myParent = parent;
            myElement = element;
        }

        /**
         * Returns the parent scope.
         *
         * @return Parent scope.
         */
        public final @NotNull Scope getParent() {
            return myParent;
        }

        /**
         * Returns the element at the root of the scope.
         *
         * @return Root of the scope.
         */
        public final @NotNull T getElement() {
            return myElement;
        }
    }

    private static final class LetOrRecursiveSet extends Psi<NixDeclarationHost> {
        private LetOrRecursiveSet(@NotNull Scope parent, @NotNull NixDeclarationHost element) {
            super(parent, element);
        }

        @Override
        public @Nullable NixSymbol resolveVariable0(@NotNull String variableName) {
            NixSymbol symbol = getElement().getSymbolForScope(variableName);
            return symbol == null ? getParent().resolveVariable0(variableName) : symbol;
        }

        @Override
        void collectVariables0(@NotNull Map<String, NixSymbol> result) {
            NixDeclarationHost element = getElement();
            for (String variableName : element.getVariableNamesForScope()) {
                if (!result.containsKey(variableName)) {
                    NixSymbol symbol = element.getSymbolForScope(variableName);
                    if (symbol != null) {
                        result.put(variableName, symbol);
                    }
                }
            }
            getParent().collectVariables0(result);
        }
    }

    private static final class With extends Psi<NixExprWith> {
        private With(@NotNull Scope parent, @NotNull NixExprWith element) {
            super(parent, element);
        }

        @Override
        public @Nullable NixSymbol resolveVariable0(@NotNull String variableName) {
            // TODO with-expression reference support
            return getParent().resolveVariable0(variableName);
        }

        @Override
        void collectVariables0(@NotNull Map<String, NixSymbol> result) {
            // TODO with-expression reference support
            getParent().collectVariables0(result);
        }
    }

    //endregion
}
