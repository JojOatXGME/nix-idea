package org.nixos.idea.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class NixReference extends PsiPolyVariantReferenceBase<PsiElement> {
    public NixReference(@NotNull PsiElement element, TextRange range) {
        super(element, range, false); // TODO: 09.05.2021 Or is it soft?
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        return new ResolveResult[0];
    }

    @Override
    public Object @NotNull [] getVariants() {
        return lookup(__ -> true).keySet().toArray();
    }

    private @NotNull Map<String, Void> lookup(@NotNull Predicate<String> filter) {
        // TODO: 09.05.2021 Test precedence on conflicts.
        // TODO: 09.05.2021 Test conflicts for sub-attributes. (x.y and x.z defined in separate expressions.)
        Map<String, Void> result = new HashMap<>();
        myElement.accept(new NixVisitor() {
            @Override
            public void visitSet(@NotNull NixSet o) {
                if (o.getRec() != null) {
                    for (NixBind bindings : o.getBindList()) {
                        bindings.accept(this);
                    }
                }
                super.visitSet(o);
            }

            @Override
            public void visitExprLet(@NotNull NixExprLet o) {
                for (NixBind bindings : o.getBindList()) {
                    bindings.accept(this);
                }
                super.visitExprLet(o);
            }

            @Override
            public void visitExprWith(@NotNull NixExprWith o) {
                super.visitExprWith(o);
                NixExpr importExpr = o.getImport();
                if (importExpr != null) {
                    //
                }
            }

            @Override
            public void visitElement(@NotNull PsiElement element) {
                super.visitElement(element);
                PsiElement parent = element.getParent();
                if (parent != null) {
                    parent.accept(this);
                }
            }
        });
        return result;
    }
}
