package org.nixos.idea.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.file.NixFile;
import org.nixos.idea.lang.NixLanguage;

import java.util.ArrayList;
import java.util.List;

public final class NixElementFactory {
    private NixElementFactory() {} // Class cannot be instantiated.

    public static @NotNull NixParamName createParamName(@NotNull Project project, @NotNull String name) {
        List<NixParamName> matches = findElements(createFile(project, name + ":0"), NixParamName.class);
        if (matches.size() == 1 && matches.get(0).getText().equals(name)) {
            return matches.get(0);
        }
        else {
            // TODO: 09.05.2021 ...
            throw new UnsupportedOperationException();
        }
    }

    public static @NotNull NixAttrPath createAttrPath(@NotNull Project project, @NotNull String path) {
        List<NixAttrPath> matches = findElements(createFile(project, "{" + path + "=0;}"), NixAttrPath.class);
        if (matches.size() == 1 && matches.get(0).getText().equals(path)) {
            return matches.get(0);
        }
        else {
            // TODO: 09.05.2021 ...
            throw new UnsupportedOperationException();
        }
    }

    public static @NotNull NixFile createFile(@NotNull Project project, @NotNull CharSequence text) {
        return (NixFile) PsiFileFactory
                .getInstance(project)
                .createFileFromText("dummy.nix", NixLanguage.INSTANCE, text);
    }

    private static <T extends PsiElement> @NotNull List<T> findElements(@NotNull NixFile file, @NotNull Class<T> clazz) {
        List<T> container = new ArrayList<>(1);
        file.acceptChildren(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (clazz.isInstance(element)) {
                    container.add(clazz.cast(element));
                }
                else {
                    super.visitElement(element);
                }
            }
        });
        return container;
    }
}
