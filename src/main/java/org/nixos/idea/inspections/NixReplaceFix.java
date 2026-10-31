package org.nixos.idea.inspections;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.psi.NixElementFactory;
import org.nixos.idea.psi.NixPsiElement;

/**
 * Quick fix which replaces an element with the given code.
 * The code is parsed as the same kind of element as the replaced element (see {@link NixElementFactory#createLike}).
 * Whitespace is inserted automatically if the new code would otherwise merge with adjacent tokens.
 */
public final class NixReplaceFix extends PsiUpdateModCommandAction<NixPsiElement> {

    private final @NotNull String myReplacement;
    private final @NotNull @Nls String myName;

    /**
     * Creates a quick fix which replaces {@code element} with {@code replacement}.
     *
     * @param element     the element which shall be replaced
     * @param replacement the code which shall be placed instead of the element
     * @param name        the name of the quick fix as shown to the user
     */
    public NixReplaceFix(@NotNull NixPsiElement element, @NotNull String replacement,
                         @NotNull @Nls String name) {
        super(element);
        myReplacement = replacement;
        myName = name;
    }

    @Override
    public @NotNull String getFamilyName() {
        return myName;
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull NixPsiElement element, @NotNull ModPsiUpdater updater) {
        element.replace(NixElementFactory.createLike(element, myReplacement));
    }
}
