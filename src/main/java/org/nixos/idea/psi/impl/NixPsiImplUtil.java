package org.nixos.idea.psi.impl;

import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.psi.*;

public final class NixPsiImplUtil {
    public static void someTest(NixExpr expr) {
    }
    public static PsiReference @NotNull [] getReferences(NixExpr expr) {
        return PsiReference.EMPTY_ARRAY;
    }
}
