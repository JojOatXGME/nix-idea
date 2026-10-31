package org.nixos.idea.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for inspections of Nix code.
 * Each inspection checks elements of one type, which is specified via the constructor.
 * The inspection then implements {@link #check(PsiElement, ProblemsHolder)}
 * which is called for every element of this type.
 * Problems are reported via {@link ProblemsHolder#problem(PsiElement, String)}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public final class NixUnquotedUriInspection extends NixInspection<NixExprUri> {
 *     public NixUnquotedUriInspection() {
 *         super(NixExprUri.class);
 *     }
 *
 *     @Override
 *     protected void check(@NotNull NixExprUri uri, @NotNull ProblemsHolder holder) {
 *         holder.problem(uri, "Unquoted URL").register();
 *     }
 * }
 * }</pre>
 *
 * <h2>Registration</h2>
 * Each inspection must be registered as {@code localInspection} in {@code plugin.xml},
 * and must have a description in {@code inspectionDescriptions/<ShortName>.html}.
 * The short name is the name of the class without the {@code Inspection} suffix.
 *
 * @param <T> the type of elements which are checked by this inspection
 */
public abstract class NixInspection<T extends PsiElement> extends LocalInspectionTool {

    private final @NotNull Class<T> myElementType;

    protected NixInspection(@NotNull Class<T> elementType) {
        myElementType = elementType;
    }

    /**
     * Checks the given element and reports problems to the given holder.
     *
     * @param element the element to check
     * @param holder  the holder for reporting problems
     */
    protected abstract void check(@NotNull T element, @NotNull ProblemsHolder holder);

    @Override
    public final @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (myElementType.isInstance(element)) {
                    check(myElementType.cast(element), holder);
                }
            }
        };
    }
}
