package org.nixos.idea.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition.SpaceRequirements;
import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.psi.NixTokenSets;
import org.nixos.idea.psi.NixTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link NixParserDefinition#spaceExistenceTypeBetweenTokens(ASTNode, ASTNode)}.
 * <p>
 * Instead of maintaining a list of token combinations, this class asks the lexer.
 * It lexes the code around the two tokens once without and once with a space between them.
 * If both variants produce the same tokens, the space is optional.
 * Otherwise, the space is either required (the tokens would merge, like {@code a -b} becoming {@code a-b})
 * or forbidden (the tokens are part of a string or path, where a space would become part of the literal).
 * <p>
 * The lexer of Nix is context-sensitive.
 * The surrounding code is therefore extended to the start or end of enclosing strings and paths,
 * so that the lexer can start in its initial state.
 * The code is also extended by one adjacent token on each side,
 * to detect cases where more than two tokens merge, like {@code x: x} becoming the URI {@code x:x}.
 */
final class NixSpaceRequirements {

    /** Elements which are lexed in a state where whitespace is not ignored. */
    private static final TokenSet LITERALS = TokenSet.create(NixTypes.STD_STRING, NixTypes.IND_STRING, NixTypes.EXPR_STD_PATH);

    private NixSpaceRequirements() {} // Cannot be instantiated

    static @NotNull SpaceRequirements between(@NotNull ASTNode left, @NotNull ASTNode right) {
        left = Objects.requireNonNullElse(TreeUtil.findLastLeaf(left, false), left);
        right = Objects.requireNonNullElse(TreeUtil.findFirstLeaf(right, false), right);
        if (left.getElementType() == NixTypes.SCOMMENT) {
            return SpaceRequirements.MUST_LINE_BREAK;
        }

        CharSequence leftText = leftContext(left);
        CharSequence rightText = rightContext(right);
        List<Token> withoutSpace;
        List<Token> withSpace;
        try {
            withoutSpace = lex(leftText + "" + rightText);
            withSpace = lex(leftText + " " + rightText);
        } catch (RuntimeException e) {
            // The lexer throws if the context is not lexable on its own, e.g. due to unbalanced braces.
            // We cannot say anything about the tokens in this case.
            return SpaceRequirements.MAY;
        }
        if (withoutSpace.equals(withSpace)) {
            return SpaceRequirements.MAY;
        } else if (hasBoundaryAt(withoutSpace, leftText.length(), left, right)) {
            // The tokens are lexed correctly without a space, but a space would change the result.
            return SpaceRequirements.MUST_NOT;
        } else {
            return SpaceRequirements.MUST;
        }
    }

    /** Returns the code which ends with the given leaf, and starts at a position where the lexer can start. */
    private static @NotNull CharSequence leftContext(@NotNull ASTNode leaf) {
        int start = contextOf(leaf).getStartOffset();
        ASTNode prev = adjacentLeaf(leaf, false);
        if (prev != null) {
            start = Math.min(start, contextOf(prev).getStartOffset());
        }
        int end = leaf.getStartOffset() + leaf.getTextLength();
        return rootOf(leaf).getChars().subSequence(start, end);
    }

    /** Returns the code which starts with the given leaf, and ends at a position where the lexer would end. */
    private static @NotNull CharSequence rightContext(@NotNull ASTNode leaf) {
        ASTNode context = contextOf(leaf);
        int end = context.getStartOffset() + context.getTextLength();
        ASTNode next = adjacentLeaf(leaf, true);
        if (next != null) {
            ASTNode nextContext = contextOf(next);
            end = Math.max(end, nextContext.getStartOffset() + nextContext.getTextLength());
        }
        return rootOf(leaf).getChars().subSequence(leaf.getStartOffset(), end);
    }

    /**
     * Returns the outermost string or path containing the given leaf, or the leaf itself.
     * Does not leave the expression of an interpolation,
     * as the lexer can start in its initial state at the expression of an interpolation.
     */
    private static @NotNull ASTNode contextOf(@NotNull ASTNode leaf) {
        ASTNode result = leaf;
        ASTNode child = leaf;
        for (ASTNode node = leaf.getTreeParent(); node != null; child = node, node = node.getTreeParent()) {
            IElementType type = node.getElementType();
            if (type == NixTypes.INTERPOLATION) {
                if (child.getFirstChildNode() != null) {
                    break; // We are coming from the expression inside the interpolation.
                }
                result = node;
            } else if (LITERALS.contains(type)) {
                result = node;
            }
        }
        return result;
    }

    /** Returns the next or previous leaf, if it is not separated by whitespace or comments. */
    private static @Nullable ASTNode adjacentLeaf(@NotNull ASTNode leaf, boolean forward) {
        ASTNode current = leaf;
        do {
            current = forward ? TreeUtil.nextLeaf(current) : TreeUtil.prevLeaf(current);
        } while (current != null && current.getTextLength() == 0);
        if (current == null ||
                current.getElementType() == TokenType.WHITE_SPACE ||
                NixTokenSets.COMMENTS.contains(current.getElementType())) {
            return null;
        }
        return current;
    }

    private static @NotNull ASTNode rootOf(@NotNull ASTNode node) {
        ASTNode root = node;
        while (root.getTreeParent() != null) {
            root = root.getTreeParent();
        }
        return root;
    }

    /** Checks that the tokens contain the two given leaves next to each other at the given offset. */
    private static boolean hasBoundaryAt(@NotNull List<Token> tokens, int offset, @NotNull ASTNode left, @NotNull ASTNode right) {
        boolean leftMatches = left.getTextLength() == 0;
        boolean rightMatches = right.getTextLength() == 0;
        for (Token token : tokens) {
            if (token.end == offset && token.type == left.getElementType()) {
                leftMatches = true;
            }
            if (token.start == offset && token.type == right.getElementType()) {
                rightMatches = true;
            }
        }
        return leftMatches && rightMatches;
    }

    /** Lexes the given text and returns all tokens, except whitespace and empty tokens. */
    private static @NotNull List<Token> lex(@NotNull CharSequence text) {
        List<Token> result = new ArrayList<>();
        Lexer lexer = new NixLexer();
        lexer.start(text);
        for (IElementType type; (type = lexer.getTokenType()) != null; lexer.advance()) {
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            if (type != TokenType.WHITE_SPACE && start != end) {
                result.add(new Token(type, start, end, text.subSequence(start, end).toString()));
            }
        }
        return result;
    }

    /**
     * Token produced by the lexer. The offsets are ignored by {@link #equals(Object)},
     * as the space shifts the offsets of all subsequent tokens.
     */
    private record Token(@NotNull IElementType type, int start, int end, @NotNull String text) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Token other && type == other.type && text.equals(other.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, text);
        }
    }
}
