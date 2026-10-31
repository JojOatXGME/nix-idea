package org.nixos.idea.inspections;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.lang.references.NixSymbolDeclaration;
import org.nixos.idea.psi.NixBindAttr;
import org.nixos.idea.psi.NixBindInherit;
import org.nixos.idea.psi.NixPsiElement;
import org.nixos.idea.psi.NixPsiUtil;

/**
 * Quick fix which deletes an element (see {@link NixPsiUtil#deleteWithWhitespace(PsiElement)}).
 */
public final class NixDeleteFix extends PsiUpdateModCommandAction<PsiElement> {

    private final @NotNull @Nls String myName;

    /**
     * Creates a quick fix which deletes the given element.
     *
     * @param element the element which shall be deleted
     * @param name    the name of the quick fix as shown to the user
     */
    public NixDeleteFix(@NotNull PsiElement element, @NotNull @Nls String name) {
        super(element);
        myName = name;
    }

    /**
     * Creates a quick fix which deletes the given declaration.
     * For bindings like {@code x.y = ...;}, the whole binding is removed.
     * For {@code inherit}, only the given attribute is removed,
     * unless it is the only attribute of the {@code inherit} statement.
     * Returns {@code null} for declarations which cannot be deleted independently, like parameters of a function.
     *
     * @param declaration the declaration which shall be deleted
     * @param name        the name of the quick fix as shown to the user
     * @return the quick fix, or {@code null}
     */
    public static @Nullable NixDeleteFix declaration(@NotNull NixSymbolDeclaration declaration, @NotNull @Nls String name) {
        NixPsiElement element = declaration.getDeclaringElement();
        if (element instanceof NixBindAttr) {
            return new NixDeleteFix(element, name);
        } else if (element instanceof NixBindInherit inherit) {
            return new NixDeleteFix(inherit.getAttributes().size() == 1 ? inherit : declaration.getIdentifier(), name);
        } else {
            return null;
        }
    }

    @Override
    public @NotNull String getFamilyName() {
        return myName;
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
        NixPsiUtil.deleteWithWhitespace(element);
    }
}
