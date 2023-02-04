package org.nixos.idea.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.file.NixFile;
import org.nixos.idea.lang.NixLanguage;

public final class NixElementFactory {
    private static final TokenSet TOKEN_SET_ID = TokenSet.create(NixTypes.ID);

    private NixElementFactory() {} // Class cannot be instantiated.

    public static @NotNull PsiElement createId(@NotNull Project project, @NotNull String name) {
        PsiElement element = createElement(project, name, TOKEN_SET_ID);
        if (element != null && element.getTextLength() == name.length()) {
            return element;
        }
        else {
            throw new IllegalArgumentException();
        }
    }

//    public static @NotNull NixParamName createParamName(@NotNull Project project, @NotNull String name) {
//        List<NixParamName> matches = findElements(createFile(project, name + ":0"), NixParamName.class);
//        if (matches.size() == 1 && matches.get(0).getText().equals(name)) {
//            return matches.get(0);
//        }
//        else {
//            // TODO: 09.05.2021 ...
//            throw new UnsupportedOperationException();
//        }
//    }
//
//    public static @NotNull NixAttrPath createAttrPath(@NotNull Project project, @NotNull String path) {
//        List<NixAttrPath> matches = findElements(createFile(project, "{" + path + "=0;}"), NixAttrPath.class);
//        if (matches.size() == 1 && matches.get(0).getText().equals(path)) {
//            return matches.get(0);
//        }
//        else {
//            // TODO: 09.05.2021 ...
//            throw new UnsupportedOperationException();
//        }
//    }

    public static @Nullable PsiElement createElement(@NotNull Project project, @NotNull CharSequence source, @NotNull TokenSet type) {
        ASTNode node = createFile(project, source).getNode().findChildByType(NixTypes.ID);
        return node == null ? null : node.getPsi();
    }

    public static @NotNull NixFile createFile(@NotNull Project project, @NotNull CharSequence text) {
        return (NixFile) PsiFileFactory
                .getInstance(project)
                .createFileFromText("dummy.nix", NixLanguage.INSTANCE, text);
    }

//    private static <T extends PsiElement> @NotNull List<T> findElements(@NotNull NixFile file, @NotNull Class<T> clazz) {
//        List<T> container = new ArrayList<>(1);
//        file.acceptChildren(new PsiRecursiveElementVisitor() {
//            @Override
//            public void visitElement(@NotNull PsiElement element) {
//                if (clazz.isInstance(element)) {
//                    container.add(clazz.cast(element));
//                }
//                else {
//                    super.visitElement(element);
//                }
//            }
//        });
//        return container;
//    }
}
