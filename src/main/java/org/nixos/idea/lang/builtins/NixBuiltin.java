package org.nixos.idea.lang.builtins;

import org.jetbrains.annotations.NotNull;
import org.nixos.idea.util.NixVersion;

import java.util.Map;

public final class NixBuiltin {
    private final @NotNull String name;
    private final @NotNull Map<String, NixVersion> since;

    private NixBuiltin(@NotNull String name, @NotNull Map<String, NixVersion> since) {
        this.name = name;
        this.since = Map.copyOf(since);
    }
}
