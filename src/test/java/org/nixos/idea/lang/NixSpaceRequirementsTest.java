package org.nixos.idea.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition.SpaceRequirements;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.nixos.idea._testutil.WithIdeaPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithIdeaPlatform.OnEdt
final class NixSpaceRequirementsTest {

    private static final NixParserDefinition PARSER_DEFINITION = new NixParserDefinition();

    private final Project myProject;

    NixSpaceRequirementsTest(Project project) {
        myProject = project;
    }

    static Stream<Path> parsingTestFiles() throws IOException {
        Path baseDir = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).resolve("ParsingTest");
        try (Stream<Path> files = Files.list(baseDir)) {
            return files.filter(path -> path.toString().endsWith(".nix")).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("parsingTestFiles")
    void consistentWithLexerForTestFiles(Path file) throws IOException {
        assertConsistentWithLexer(Files.readString(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "x: x",
            "x:x",
            "a - b",
            "a -b",
            "a-b",
            "1 - 2",
            "1.5 .5",
            "./a + ./b",
            "./a/b / c",
            "a ./b",
            "a - ./b",
            "./a/${b}/c",
            "~/a",
            "<nixpkgs> <nixpkgs/lib>",
            "\"${a}${b}\"",
            "\"a ${ b } c\"",
            "''${a} b''",
            "''a\n  ''${b}\n''",
            "{ ${a} = 1; }",
            "a.${b}",
            "a.\"b\".c",
            "if a then b else c",
            "x: # comment\n x",
            "x: /* comment */ x",
            "a//b",
            "a ++ b",
            "a.b or c",
            "{ a, b ? 1, ... }@args: a",
            "let a = 1; in a",
            "https://example.org/a b",
    })
    void consistentWithLexer(String code) {
        assertConsistentWithLexer(code);
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '|', value = {
            // code      , offset of right token, expected requirement
            "|x: x|      , 3, MUST",
            "|a - b|     , 2, MUST", // a- is an identifier
            "|a - b|     , 4, MAY",
            "|a -b|      , 2, MUST",
            "|a ./b|     , 2, MUST",
            "|./a + b|   , 4, MUST",
            "|(./a) b|   , 6, MAY",
            "|\"a${b}\"| , 2, MUST_NOT",
            "|\"${a}${b}\"|, 5, MUST_NOT",
            "|\"${ a }\"|, 4, MAY",
            "|{ ${a} = 1; }|, 3, MUST_NOT",
            "|# c\nx|    , 4, MUST_LINE_BREAK",
            "|[ a b ]|   , 4, MUST",
            "|[ a \"b\" ]|, 4, MAY",
    })
    void requirement(String code, int rightOffset, SpaceRequirements expected) {
        ASTNode root = parse(code).getNode();
        ASTNode right = root.findLeafElementAt(rightOffset);
        ASTNode left = previousToken(right);
        assertEquals(expected, PARSER_DEFINITION.spaceExistenceTypeBetweenTokens(left, right));
    }

    @Test
    void tokensFromDifferentFiles() {
        ASTNode left = lastToken(parse("a"));
        ASTNode right = TreeUtil.findFirstLeaf(parse("b").getNode());
        assertEquals(SpaceRequirements.MUST, PARSER_DEFINITION.spaceExistenceTypeBetweenTokens(left, right));
        right = TreeUtil.findFirstLeaf(parse("(b)").getNode());
        assertEquals(SpaceRequirements.MAY, PARSER_DEFINITION.spaceExistenceTypeBetweenTokens(left, right));
    }

    /**
     * Verifies the result for every pair of consecutive tokens in the given code by modifying the whitespace
     * between them, and checking whether the lexer still produces the same tokens.
     */
    private void assertConsistentWithLexer(String code) {
        List<String> originalTokens = lex(code);
        List<ASTNode> tokens = new ArrayList<>();
        for (ASTNode leaf = TreeUtil.findFirstLeaf(parse(code).getNode()); leaf != null; leaf = TreeUtil.nextLeaf(leaf)) {
            if (leaf.getElementType() != TokenType.WHITE_SPACE) {
                tokens.add(leaf);
            }
        }
        for (int i = 1; i < tokens.size(); i++) {
            ASTNode left = tokens.get(i - 1);
            ASTNode right = tokens.get(i);
            int gapStart = left.getStartOffset() + left.getTextLength();
            int gapEnd = right.getStartOffset();
            String gap = code.substring(gapStart, gapEnd);
            SpaceRequirements requirement = PARSER_DEFINITION.spaceExistenceTypeBetweenTokens(left, right);
            String message = String.format("Between %s and %s at offset %d in:\n%s\nGot: %s",
                    left.getElementType(), right.getElementType(), gapStart, code, requirement);
            List<String> withoutGap = lex(code.substring(0, gapStart) + code.substring(gapEnd));
            List<String> withSpace = lex(code.substring(0, gapStart) + " " + code.substring(gapStart));
            switch (requirement) {
                case MAY -> {
                    assertEquals(originalTokens, withoutGap, message);
                    assertEquals(originalTokens, withSpace, message);
                }
                case MUST -> {
                    assertTrue(gap.contains(" ") || gap.contains("\n"), message);
                    assertTrue(!originalTokens.equals(withoutGap), message);
                }
                case MUST_NOT -> {
                    assertEquals("", gap, message);
                    assertTrue(!originalTokens.equals(withSpace), message);
                }
                case MUST_LINE_BREAK -> assertTrue(gap.contains("\n"), message);
            }
        }
    }

    private PsiFile parse(String code) {
        return PsiFileFactory.getInstance(myProject).createFileFromText("test.nix", NixLanguage.INSTANCE, code);
    }

    private static ASTNode lastToken(PsiFile file) {
        return TreeUtil.findLastLeaf(file.getNode());
    }

    private static ASTNode previousToken(ASTNode node) {
        ASTNode result = TreeUtil.prevLeaf(node);
        while (result != null && result.getElementType() == TokenType.WHITE_SPACE) {
            result = TreeUtil.prevLeaf(result);
        }
        return result;
    }

    /** Returns all tokens except whitespace and empty tokens. */
    private static @NotNull List<String> lex(String code) {
        List<String> result = new ArrayList<>();
        Lexer lexer = new NixLexer();
        lexer.start(code);
        for (IElementType type; (type = lexer.getTokenType()) != null; lexer.advance()) {
            if (type != TokenType.WHITE_SPACE && lexer.getTokenStart() != lexer.getTokenEnd()) {
                result.add(type + ":" + lexer.getTokenText());
            }
        }
        return result;
    }
}
