package org.nixos.idea.lang.references;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.lang.references.symbol.NixUserSymbol;
import org.nixos.idea.psi.NixPsiElement;
import org.nixos.idea.settings.NixSymbolSettings;

import java.util.HashSet;
import java.util.Set;

/**
 * Utilities for working with symbols.
 */
@SuppressWarnings("UnstableApiUsage")
public final class NixSymbolUtil {

    private static final Key<CachedValue<Set<Symbol>>> KEY_REFERENCED_SYMBOLS = Key.create("NixSymbolUtil.referencedSymbols");

    private NixSymbolUtil() {} // Cannot be instantiated

    /**
     * Returns whether there is any reference to the given symbol.
     * Declarations of the symbol are not considered as references.
     * The result is cached for the whole file,
     * so calling this method for all symbols of a file is cheap.
     * Note that this method only finds references when {@link NixSymbolSettings#getEnabled()} is {@code true}.
     *
     * @param symbol the symbol to check
     * @return {@code true} if the symbol is referenced somewhere in its file
     */
    public static boolean isReferenced(@NotNull NixUserSymbol symbol) {
        PsiFile file = symbol.getHost().getContainingFile();
        return file != null && getReferencedSymbols(file).contains(symbol);
    }

    private static @NotNull Set<Symbol> getReferencedSymbols(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, KEY_REFERENCED_SYMBOLS, () -> {
            Set<Symbol> result = new HashSet<>();
            PsiTreeUtil.processElements(file, NixPsiElement.class, element -> {
                for (PsiSymbolReference reference : element.getOwnReferences()) {
                    result.addAll(reference.resolveReference());
                }
                return true;
            });
            NixSymbolSettings settings = NixSymbolSettings.getInstance();
            ModificationTracker settingsTracker = settings::getStateModificationCount;
            return CachedValueProvider.Result.create(result, file, settingsTracker);
        });
    }
}
