package org.nixos.idea.lang.references.symbol;

import com.intellij.find.usages.api.SearchTarget;
import com.intellij.find.usages.api.UsageHandler;
import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.lang.builtins.NixBuiltin;

@SuppressWarnings("UnstableApiUsage")
public abstract sealed class NixSymbol implements Symbol, SearchTarget
        permits NixBuiltinSymbol, NixUserSymbol {

    NixSymbol() {} // Can only be implemented within this package

    @Contract(pure = true)
    public static @NotNull NixSymbol builtin(@NotNull NixBuiltin builtin) {
        return new NixBuiltinSymbol(builtin);
    }

    @Contract(pure = true)
    public abstract @NotNull String getName();

    /**
     * Returns the builtin represented by this symbol.
     *
     * @return The builtin, or {@code null} if this symbol does not represent a builtin.
     */
    @Contract(pure = true)
    public @Nullable NixBuiltin getBuiltin() {
        return null;
    }

    @Override
    public abstract @NotNull Pointer<? extends NixSymbol> createPointer();

    @Override
    public @NotNull UsageHandler getUsageHandler() {
        return UsageHandler.createEmptyUsageHandler(getName());
    }
}
