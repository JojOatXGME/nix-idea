package org.nixos.idea.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.lang.references.Scope;
import org.nixos.idea.psi.impl.NixStdAttrImpl;
import org.nixos.idea.psi.impl.NixStringAttrImpl;
import org.nixos.idea.util.NixStringUtil;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public final class NixPsiUtil {

    private static final Logger LOG = Logger.getInstance(NixPsiUtil.class);

    private NixPsiUtil() {} // Cannot be instantiated

    public static boolean isRecursive(@NotNull NixExprAttrs attrs) {
        return attrs.getNode().findChildByType(NixTypes.REC) != null ||
                attrs.getNode().findChildByType(NixTypes.LET) != null;
    }

    public static boolean isLegacyLet(@NotNull NixExprAttrs attrs) {
        return attrs.getNode().findChildByType(NixTypes.LET) != null;
    }

    public static @NotNull Collection<NixParameter> getParameters(@NotNull NixExprLambda lambda) {
        NixArgument mainParam = lambda.getArgument();
        NixFormals formalsHolder = lambda.getFormals();
        List<NixFormal> formals = formalsHolder == null ? List.of() : formalsHolder.getFormalList();
        return new AbstractCollection<>() {
            @Override
            public @NotNull Iterator<NixParameter> iterator() {
                return stream().iterator();
            }

            @Override
            public @NotNull Stream<NixParameter> stream() {
                return Stream.concat(
                        Stream.ofNullable(lambda.getArgument()),
                        formals.stream()
                );
            }

            @Override
            public int size() {
                return (mainParam == null ? 0 : 1) + formals.size();
            }
        };
    }

    public static @NotNull List<NixExpr> getArguments(@NotNull NixExprApp app) {
        List<NixExpr> expressions = app.getExprList();
        return expressions.subList(1, expressions.size());
    }

    /**
     * Returns the static name of an attribute.
     * Is {@code null} for dynamic attributes.
     *
     * @param attr the attribute
     * @return the name of the attribute or {@code null}
     */
    public static @Nullable String getAttributeName(@NotNull NixAttr attr) {
        if (attr instanceof NixStdAttrImpl) {
            return attr.getText();
        } else if (attr instanceof NixStringAttrImpl stringAttr) {
            NixStdString string = stringAttr.getStdString();
            List<NixStringPart> stringParts = string == null ? null : string.getStringParts();
            return stringParts != null && stringParts.size() == 1 &&
                    stringParts.get(0) instanceof NixStringText text
                    ? NixStringUtil.parse(text)
                    : null;
        } else {
            LOG.error("Unexpected NixAttr implementation: " + attr.getClass());
            return null;
        }
    }

    public static boolean isDeclaration(@NotNull NixIdentifier identifier) {
        return identifier instanceof NixParameterName ||
                identifier instanceof NixAttr attr && isDeclaration(attr);
    }

    public static boolean isDeclaration(@NotNull NixAttr attr) {
        return attr.getParent() instanceof NixAttrPath path &&
                path.getParent() instanceof NixBindAttr;
    }

    /**
     * Returns the scope surrounding the given element.
     * In contrast to {@link NixPsiElement#getScope()},
     * the returned scope does not contain variables declared by the given element itself.
     * This is useful to check whether a variable declared by the element shadows another variable.
     *
     * @param element the element
     * @return the scope of the parent of the given element
     */
    public static @NotNull Scope getOuterScope(@NotNull NixPsiElement element) {
        return element.getParent() instanceof NixPsiElement parent ? parent.getScope() : Scope.root();
    }

    /**
     * Returns whether {@code target} can be replaced by {@code replacement}
     * without wrapping {@code replacement} in parentheses.
     * The method is conservative.
     * It returns {@code false} if it cannot determine that the replacement is safe.
     *
     * @param target      the expression which shall be replaced
     * @param replacement the expression which shall be placed at the location of {@code target}
     * @return {@code true} if the replacement does not change the structure of the surrounding code
     */
    public static boolean canReplaceWithoutParentheses(@NotNull NixExpr target, @NotNull NixExpr replacement) {
        if (acceptsAnyExpression(target)) {
            return true;
        } else if (replacement instanceof NixExprSimple) {
            return true;
        } else if (replacement instanceof NixExprSelect select && select.getDefault() == null) {
            // `(a.b) or c` would become `a.b or c`
            // `(a.b).c or d` would become `a.b.c or d`
            return !isFollowedBy(target, NixTypes.OR_KW) &&
                    !(target.getParent() instanceof NixExprSelect parent && parent.getValue() == target && parent.getDefault() != null);
        } else {
            return false;
        }
    }

    /**
     * Returns whether the location of the given expression accepts any expression without parentheses.
     * This is the case at the root of a file and in most places which are delimited by keywords or brackets,
     * like the value of a binding or the body of a function.
     *
     * @param expr the expression
     * @return {@code true} if any expression could be placed at the location of {@code expr}
     */
    public static boolean acceptsAnyExpression(@NotNull NixExpr expr) {
        PsiElement parent = expr.getParent();
        return parent instanceof PsiFile ||
                parent instanceof NixExprParens ||
                parent instanceof NixInterpolation ||
                parent instanceof NixBindAttr ||
                parent instanceof NixBindInherit ||
                parent instanceof NixFormal ||
                parent instanceof NixExprLet ||
                parent instanceof NixExprWith ||
                parent instanceof NixExprAssert ||
                parent instanceof NixExprIf ||
                parent instanceof NixExprLambda;
    }

    private static boolean isFollowedBy(@NotNull PsiElement element, @NotNull IElementType tokenType) {
        PsiElement next = PsiTreeUtil.nextVisibleLeaf(element);
        return next != null && next.getNode().getElementType() == tokenType;
    }

    /**
     * Deletes the given element together with the whitespace in front of it.
     * If there is no whitespace in front of the element, the whitespace after the element is removed instead.
     * The method operates on the document of the file, and commits the document afterward.
     * All PSI elements of the file may therefore become invalid.
     *
     * @param element the element which shall be deleted
     */
    public static void deleteWithWhitespace(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
        Document document = file.getFileDocument();
        documentManager.doPostponedOperationsAndUnblockDocument(document);

        CharSequence text = document.getImmutableCharSequence();
        int start = element.getTextRange().getStartOffset();
        int end = element.getTextRange().getEndOffset();
        int startWithWhitespace = start;
        while (startWithWhitespace > 0 && Character.isWhitespace(text.charAt(startWithWhitespace - 1))) {
            startWithWhitespace--;
        }
        if (startWithWhitespace < start) {
            start = startWithWhitespace;
        } else {
            while (end < text.length() && Character.isWhitespace(text.charAt(end))) {
                end++;
            }
        }
        document.deleteString(start, end);
        documentManager.commitDocument(document);
    }
}
