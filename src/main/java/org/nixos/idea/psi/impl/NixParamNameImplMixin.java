package org.nixos.idea.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.psi.NixElementFactory;
import org.nixos.idea.psi.NixParamName;

import static org.nixos.idea.psi.NixTypes.ID;

abstract class NixParamNameImplMixin extends ASTWrapperPsiElement implements NixParamName {
    NixParamNameImplMixin(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull PsiElement getNameIdentifier() {
        return findNotNullChildByType(ID);
    }

    @Override
    public int getTextOffset() {
        return getNameIdentifier().getTextOffset();
    }

    @Override
    public String getName() {
        return getNameIdentifier().getText();
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        getNameIdentifier().replace(NixElementFactory.createId(getProject(), name));
        return this;
    }
}
