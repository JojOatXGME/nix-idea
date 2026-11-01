package org.nixos.idea.format;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.lang.NixLanguage;

/**
 * Re-indents the current line when typing a closing bracket at the start of a line,
 * so that the bracket gets aligned with the line containing the matching opening bracket.
 */
public final class NixTypedHandler extends TypedHandlerDelegate {

    private final NixLineIndentProvider myIndentProvider = new NixLineIndentProvider();

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if ((c == '}' || c == ']' || c == ')') && file.getLanguage().isKindOf(NixLanguage.INSTANCE)) {
            reindentClosingBracket(project, editor);
        }
        return Result.CONTINUE;
    }

    private void reindentClosingBracket(@NotNull Project project, @NotNull Editor editor) {
        Document document = editor.getDocument();
        CharSequence text = document.getCharsSequence();
        int bracketOffset = editor.getCaretModel().getOffset() - 1;
        if (bracketOffset < 0) {
            return;
        }
        int lineStart = document.getLineStartOffset(document.getLineNumber(bracketOffset));
        for (int i = lineStart; i < bracketOffset; i++) {
            if (text.charAt(i) != ' ' && text.charAt(i) != '\t') {
                return; // The bracket is not the first token on this line
            }
        }
        String indent = myIndentProvider.getLineIndent(project, editor, NixLanguage.INSTANCE, lineStart);
        if (indent == null || indent == LineIndentProvider.DO_NOT_ADJUST ||
            text.subSequence(lineStart, bracketOffset).toString().equals(indent)) {
            return;
        }
        document.replaceString(lineStart, bracketOffset, indent);
        editor.getCaretModel().moveToOffset(lineStart + indent.length() + 1);
    }
}
