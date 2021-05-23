package org.nixos.idea.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.psi.*;

import java.util.List;

public abstract class NixNamedElementImpl extends ASTWrapperPsiElement implements NixNamedElement {
    // TODO: 09.05.2021 Review and test renaming.
    NixNamedElementImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        if (this instanceof NixBindAttrImpl) {
            ASTNode pathNode = getNode().findChildByType(NixTypes.ATTR_PATH);
            return pathNode == null ? null : pathNode.getPsi();
        }
        else if (this instanceof NixParamImpl || this instanceof NixParamAttrImpl) {
            ASTNode pathNode = getNode().findChildByType(NixTypes.PARAM_NAME);
            return pathNode == null ? null : pathNode.getPsi();
        }
        else {
            throw new IllegalStateException("Unknown subclass");
        }
    }

    @Override
    public int getTextOffset() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? super.getTextOffset() : identifier.getTextOffset();
    }

    @Override
    public @Nullable String getName() {
        PsiElement identifier = getNameIdentifier();
        if (identifier == null) {
            return null;
        }
        else {
            StringBuilder builder = new StringBuilder();
            identifier.accept(new NixVisitor() {
                @Override
                public void visitParamName(@NotNull NixParamName o) {
                    builder.append(o.getNode().getChars());
                }

                @Override
                public void visitAttrPath(@NotNull NixAttrPath o) {
                    List<NixAttr> pathComponents = o.getAttrList();
                    for (NixAttr component : pathComponents) {
                        component.accept(this);
                        builder.append('.');
                    }
                    if (!pathComponents.isEmpty()) {
                        builder.setLength(builder.length() - 1);
                    }
                }

                @Override
                public void visitStdAttr(@NotNull NixStdAttr o) {
                    builder.append(o.getNode().getChars());
                }

                @Override
                public void visitStringAttr(@NotNull NixStringAttr o) {
                    // TODO: 09.05.2021 ...
                }
            });
            return builder.toString();
        }
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        PsiElement identifier = getNameIdentifier();
        if (identifier instanceof NixParamName) {
            identifier.replace(NixElementFactory.createParamName(getProject(), name));
        }
        else if (identifier instanceof NixAttrPath) {
            identifier.replace(NixElementFactory.createAttrPath(getProject(), name));
        }
        else {
            throw new IllegalStateException("Unknown identifier");
        }
        return this;
    }
}
