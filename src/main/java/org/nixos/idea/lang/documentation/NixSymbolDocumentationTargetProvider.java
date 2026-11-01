package org.nixos.idea.lang.documentation;

import com.intellij.model.Symbol;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.SymbolDocumentationTargetProvider;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.lang.builtins.NixBuiltin;
import org.nixos.idea.lang.references.symbol.NixSymbol;

import java.util.List;

/**
 * Provides quick documentation for {@link NixSymbol symbols} of the Nix language.
 * Currently, only builtins are supported.
 */
@SuppressWarnings("UnstableApiUsage")
public final class NixSymbolDocumentationTargetProvider implements SymbolDocumentationTargetProvider {

    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull Project project, @NotNull Symbol symbol) {
        NixBuiltin builtin = symbol instanceof NixSymbol nixSymbol ? nixSymbol.getBuiltin() : null;
        return builtin == null ? List.of() : List.of(new NixBuiltinDocumentationTarget(project, builtin));
    }
}
