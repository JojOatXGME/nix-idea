package org.nixos.idea.format;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.nixos.idea._testutil.WithIdeaPlatform;
import org.nixos.idea.file.NixFileType;

@WithIdeaPlatform.OnEdt
@WithIdeaPlatform.CodeInsight
final class NixLineIndentProviderTest {

    private final CodeInsightTestFixture myFixture;

    NixLineIndentProviderTest(CodeInsightTestFixture fixture) {
        myFixture = fixture;
    }

    @Test
    void topLevel() {
        enter("<caret>", "\n<caret>");
        enter("{ pkgs }:<caret>", "{ pkgs }:\n<caret>");
    }

    @Test
    void afterOpeningCurlyBrace() {
        // Note that the platform automatically inserts the closing brace
        enter("{<caret>", "{\n  <caret>\n}");
        enter("foo {<caret>", "foo {\n  <caret>\n}");
        enter("  x = {<caret>", "  x = {\n    <caret>\n  }");
    }

    @Test
    void betweenBraces() {
        enter("{<caret>}", "{\n  <caret>\n}");
        enter("[<caret>]", "[\n  <caret>\n]");
        enter("(<caret>)", "(\n  <caret>\n)");
        enter("  x = {<caret>};", "  x = {\n    <caret>\n  };");
    }

    @Test
    void afterBinding() {
        enter("{\n  a = 1;<caret>\n}", "{\n  a = 1;\n  <caret>\n}");
        enter("{\n  a = {\n    b = 1;\n  };<caret>\n}", "{\n  a = {\n    b = 1;\n  };\n  <caret>\n}");
    }

    @Test
    void beforeClosingBrace() {
        enter("{\n  a = 1;<caret>}", "{\n  a = 1;\n<caret>}");
        enter("[\n  a<caret>]", "[\n  a\n<caret>]");
    }

    @Test
    void listItems() {
        enter("[\n  a<caret>\n]", "[\n  a\n  <caret>\n]");
        enter("x = [ a<caret>", "x = [ a\n  <caret>");
    }

    @Test
    void danglingAssignment() {
        enter("{\n  a =<caret>\n}", "{\n  a =\n    <caret>\n}");
    }

    @Test
    void continuationKeepsIndent() {
        enter("{\n  a =\n    b<caret>\n}", "{\n  a =\n    b\n    <caret>\n}");
        enter("(\n  f\n    x<caret>\n)", "(\n  f\n    x\n    <caret>\n)");
    }

    @Test
    void letIn() {
        enter("let<caret>", "let\n  <caret>");
        enter("let\n  a = 1;<caret>", "let\n  a = 1;\n  <caret>");
        enter("let\n  a = 1;<caret>in a", "let\n  a = 1;\n<caret>in a");
        enter("let\n  a = 1;\nin<caret>", "let\n  a = 1;\nin\n<caret>");
        enter("{\n  x = let\n    a = 1;\n  in<caret>\n}", "{\n  x = let\n    a = 1;\n  in\n  <caret>\n}");
    }

    @Test
    void functionArguments() {
        enter("{ a,<caret>", "{ a,\n  <caret>");
    }

    @Test
    void indentedString() {
        enter("''<caret>", "''\n  <caret>");
        enter("  x = ''<caret>", "  x = ''\n    <caret>");
        enter("''\n  foo\n    bar<caret>", "''\n  foo\n    bar\n    <caret>");
        enter("''\n  foo\n\n<caret>", "''\n  foo\n\n\n  <caret>");
        enter("''\n  foo<caret>''", "''\n  foo\n<caret>''");
    }

    @Test
    void interpolationInsideIndentedString() {
        enter("''\n  ${a}<caret>", "''\n  ${a}\n  <caret>");
    }

    @Test
    void normalStringKeepsDefaultBehavior() {
        // Whitespace is significant inside normal strings, so we leave the platform's default behavior alone
        enter("{\n  a = \"foo<caret>", "{\n  a = \"foo\n  <caret>");
    }

    @Test
    void ignoresComments() {
        enter("{\n  a = 1; # comment<caret>\n}", "{\n  a = 1; # comment\n  <caret>\n}");
        enter("{ # comment<caret>", "{ # comment\n  <caret>");
    }

    @Test
    void typingClosingBracketDedents() {
        enter("x = {\n  a = [<caret>\n}", "x = {\n  a = [\n    <caret>\n}");
        myFixture.type("b\n]");
        check("x = {\n  a = [\n    b\n  ]<caret>\n}");
        myFixture.type(";\n}");
        check("x = {\n  a = [\n    b\n  ];\n}<caret>\n}");
    }

    @Test
    void typingClosingBracketMidLineDoesNothing() {
        myFixture.configureByText(NixFileType.INSTANCE, "  [ a<caret>");
        myFixture.type(']');
        check("  [ a]<caret>");
    }

    private void enter(@Language("HTML") String before, @Language("HTML") String after) {
        myFixture.configureByText(NixFileType.INSTANCE, before);
        myFixture.type('\n');
        check(after);
    }

    private void check(@Language("HTML") String expected) {
        String text = myFixture.getEditor().getDocument().getText();
        int caret = myFixture.getCaretOffset();
        Assertions.assertEquals(expected, text.substring(0, caret) + "<caret>" + text.substring(caret));
    }
}
