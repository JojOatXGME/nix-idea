package org.nixos.idea.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.psi.*;

import java.util.List;

import static org.nixos.idea.psi.NixTypes.ID;
import static org.nixos.idea.psi.NixTypes.OR_KW;

abstract class NixStdAttrImplMixin extends NixAttrImpl implements NixStdAttr {
    private static final TokenSet ID_TOKEN_TYPES = TokenSet.create(ID, OR_KW);

    NixStdAttrImplMixin(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull PsiElement getNameIdentifier() {
        return findNotNullChildByType(ID_TOKEN_TYPES);
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
        getNameIdentifier().replace(createToken(getProject(), name));
        return this;
    }

    private static @NotNull PsiElement createToken(@NotNull Project project, @NotNull String name) {
        PsiElement token = NixElementFactory.createElement(project, "{" + name + "=0;}", ID_TOKEN_TYPES);
        if (token != null && token.getText().equals(name)) {
            return token;
        }
        else {
            throw new IllegalArgumentException();
        }
    }

//    @Override
//    public @Nullable String getName() {
//        PsiElement identifier = getNameIdentifier();
//        if (identifier == null) {
//            return null;
//        }
//        else {
//            StringBuilder builder = new StringBuilder();
//            identifier.accept(new NixVisitor() {
//                @Override
//                public void visitParamName(@NotNull NixParamName o) {
//                    builder.append(o.getNode().getChars());
//                }
//
//                @Override
//                public void visitAttrPath(@NotNull NixAttrPath o) {
//                    List<NixAttr> pathComponents = o.getAttrList();
//                    for (NixAttr component : pathComponents) {
//                        component.accept(this);
//                        builder.append('.');
//                    }
//                    if (!pathComponents.isEmpty()) {
//                        builder.setLength(builder.length() - 1);
//                    }
//                }
//
//                @Override
//                public void visitStdAttr(@NotNull NixStdAttr o) {
//                    builder.append(o.getNode().getChars());
//                }
//
//                @Override
//                public void visitStringAttr(@NotNull NixStringAttr o) {
//                    // TODO: 09.05.2021 ...
//                }
//            });
//            return builder.toString();
//        }
//    }
}
