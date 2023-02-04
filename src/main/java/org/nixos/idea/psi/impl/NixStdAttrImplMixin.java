package org.nixos.idea.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.psi.NixBindAttr;

abstract class NixBindAttrImplMixin extends NixBindImpl implements NixBindAttr {
    NixBindAttrImplMixin(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        return getAttrPath();
    }

    @Override
    public int getTextOffset() {
        return getAttrPath().getTextOffset();
    }
}
