package org.nixos.idea.lang.builtins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * List of globally available symbols.
 * Start {@code nix repl} and press <kbd>Tab</kbd> to compare against your Nix installation.
 */
public final class NixGlobalScope {
    private static final Symbol[] GLOBAL_SCOPE = {
            literal("false"),
            literal("null"),
            literal("true"),
            builtin("abort"),
            builtin("baseNameOf"),
            builtin("break"),
            builtin("builtins"),
            builtin("derivation"),
            builtin("derivationStrict"),
            builtin("dirOf"),
            builtin("fetchGit"),
            builtin("fetchMercurial"),
            builtin("fetchTarball"),
            builtin("fetchTree"),
            builtin("fromTOML"),
            builtin("import"),
            builtin("isNull"),
            builtin("map"),
            builtin("placeholder"),
            builtin("removeAttrs"),
            builtin("scopedImport"),
            builtin("throw"),
            builtin("toString"),
    };

    private static @NotNull Symbol literal(@NotNull String name) {
        return new Symbol(name, null);
    }

    private static @NotNull Symbol builtin(@NotNull String name) {
        return new Symbol(name, null);
    }

    public static final class Symbol {
        private final @NotNull String name;
        private final @Nullable NixBuiltin target;

        public Symbol(@NotNull String name, @Nullable NixBuiltin target) {
            this.name = name;
            this.target = target;
        }
    }
}
