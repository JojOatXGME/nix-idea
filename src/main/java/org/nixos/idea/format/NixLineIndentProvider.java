package org.nixos.idea.format;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.lang.NixLanguage;
import org.nixos.idea.psi.NixTokenSets;
import org.nixos.idea.psi.NixTypes;

import java.util.Map;

/**
 * Calculates the indentation of new lines, for example when pressing <kbd>Enter</kbd>.
 * <p>
 * The implementation only looks at the tokens of the editor highlighter, so it does not need an up-to-date PSI tree and
 * works independently of any formatter. The rules are intentionally simple:
 * <ul>
 *     <li>Lines inside a block ({@code { }}, {@code [ ]}, {@code ( )}, {@code let … in}, {@code '' ''}) are indented
 *     by one level relative to the line containing the opening token.
 *     <li>A line starting with the token which closes the block gets the indentation of the line which opened it.
 *     <li>A line following a dangling {@code =} gets one additional level of indentation.
 *     <li>Otherwise, the indentation of the line containing the previous token is kept.
 * </ul>
 */
public final class NixLineIndentProvider implements LineIndentProvider {

    private static final Map<IElementType, IElementType> CLOSER_FOR_OPENER = Map.of(
            NixTypes.LCURLY, NixTypes.RCURLY,
            NixTypes.LBRAC, NixTypes.RBRAC,
            NixTypes.LPAREN, NixTypes.RPAREN,
            NixTypes.LET, NixTypes.IN,
            NixTypes.STRING_OPEN, NixTypes.STRING_CLOSE,
            NixTypes.IND_STRING_OPEN, NixTypes.IND_STRING_CLOSE);
    private static final TokenSet OPENERS = TokenSet.create(CLOSER_FOR_OPENER.keySet().toArray(IElementType[]::new));
    private static final TokenSet CLOSERS = TokenSet.create(CLOSER_FOR_OPENER.values().toArray(IElementType[]::new));
    private static final TokenSet IGNORED = TokenSet.orSet(NixTokenSets.WHITE_SPACES, NixTokenSets.COMMENTS);
    private static final TokenSet SEPARATORS = TokenSet.create(NixTypes.SEMI, NixTypes.COMMA);

    @Override
    public @Nullable String getLineIndent(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
        if (!(editor instanceof EditorEx editorEx)) {
            return null;
        }
        Document document = editor.getDocument();
        CharSequence text = document.getCharsSequence();
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        if (lineStart == 0) {
            return "";
        }

        // First token on the current line (if any), used to detect closing tokens.
        IElementType nextToken = null;
        int nextPos = skipSpaces(text, offset);
        if (nextPos < text.length() && text.charAt(nextPos) != '\n') {
            nextToken = editorEx.getHighlighter().createIterator(nextPos).getTokenType();
        }

        // Search backwards for the innermost unclosed opening token and the previous significant token.
        IElementType opener = null;
        int openerStart = -1;
        IElementType prev = null;
        int prevStart = -1;
        int depth = 0;
        HighlighterIterator it = editorEx.getHighlighter().createIterator(lineStart - 1);
        for (; !it.atEnd(); it.retreat()) {
            IElementType type = it.getTokenType();
            if (type == null || IGNORED.contains(type)) {
                continue;
            }
            if (prev == null) {
                prev = type;
                prevStart = it.getStart();
            }
            if (CLOSERS.contains(type)) {
                depth++;
            } else if (OPENERS.contains(type)) {
                if (depth == 0) {
                    opener = type;
                    openerStart = it.getStart();
                    break;
                }
                depth--;
            }
        }

        String unit = indentUnit(project, document);
        String base = opener == null ? "" : indentOfLineAt(text, openerStart);
        if (opener == NixTypes.STRING_OPEN) {
            // Whitespace is significant within normal strings
            return DO_NOT_ADJUST;
        }
        if (opener != null && nextToken == CLOSER_FOR_OPENER.get(opener)) {
            return base;
        }
        if (opener == NixTypes.IND_STRING_OPEN) {
            int previousLine = previousNonBlankLineStart(text, lineStart);
            return previousLine <= openerStart ? base + unit : indentOfLineAt(text, previousLine);
        }
        if (prev == null) {
            return "";
        }
        if (prev == opener || SEPARATORS.contains(prev) || opener == NixTypes.LBRAC) {
            return opener == null ? "" : base + unit;
        }
        if (prev == NixTypes.ASSIGN) {
            return indentOfLineAt(text, prevStart) + unit;
        }
        return indentOfLineAt(text, prevStart);
    }

    @Override
    public boolean isSuitableFor(@Nullable Language language) {
        return language != null && language.isKindOf(NixLanguage.INSTANCE);
    }

    private static @NotNull String indentUnit(@NotNull Project project, @NotNull Document document) {
        CommonCodeStyleSettings.IndentOptions options = CodeStyle.getIndentOptions(project, document);
        return options.USE_TAB_CHARACTER ? "\t" : " ".repeat(options.INDENT_SIZE);
    }

    private static int skipSpaces(@NotNull CharSequence text, int offset) {
        while (offset < text.length() && (text.charAt(offset) == ' ' || text.charAt(offset) == '\t')) {
            offset++;
        }
        return offset;
    }

    private static int lineStartOf(@NotNull CharSequence text, int offset) {
        while (offset > 0 && text.charAt(offset - 1) != '\n') {
            offset--;
        }
        return offset;
    }

    private static @NotNull String indentOfLineAt(@NotNull CharSequence text, int offset) {
        int start = lineStartOf(text, offset);
        return text.subSequence(start, skipSpaces(text, start)).toString();
    }

    /**
     * Returns the start offset of the closest line before {@code lineStart} which contains non-whitespace characters,
     * or {@code 0} if there is no such line.
     */
    private static int previousNonBlankLineStart(@NotNull CharSequence text, int lineStart) {
        while (lineStart > 0) {
            lineStart = lineStartOf(text, lineStart - 1);
            int contentStart = skipSpaces(text, lineStart);
            if (contentStart < text.length() && text.charAt(contentStart) != '\n') {
                return lineStart;
            }
        }
        return 0;
    }
}
