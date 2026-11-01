package org.nixos.idea.lang.completion;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.nixos.idea.file.NixFileType;
import org.nixos.idea.settings.NixSymbolSettings;

import java.util.List;

public class NixCompletionContributorTest extends BasePlatformTestCase {

    private boolean mySymbolsEnabled;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mySymbolsEnabled = NixSymbolSettings.getInstance().getEnabled();
        NixSymbolSettings.getInstance().setEnabled(true);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            NixSymbolSettings.getInstance().setEnabled(mySymbolsEnabled);
        } finally {
            super.tearDown();
        }
    }

    public void testUserVariablesRequireSymbolSupport() {
        NixSymbolSettings.getInstance().setEnabled(false);
        List<String> lookups = complete("let alpha = 1; in <caret>");
        assertDoesntContain(lookups, "alpha");
        assertContainsElements(lookups, "builtins");
    }

    public void testLetVariables() {
        List<String> lookups = complete("let alpha = 1; beta = 2; in <caret>");
        assertContainsElements(lookups, "alpha", "beta");
    }

    public void testLambdaParameters() {
        List<String> lookups = complete("{ alpha, beta ? 1, ... }@gamma: <caret>");
        assertContainsElements(lookups, "alpha", "beta", "gamma");
    }

    public void testSimpleLambdaParameter() {
        List<String> lookups = complete("alpha: <caret>");
        assertContainsElements(lookups, "alpha");
    }

    public void testRecursiveSet() {
        List<String> lookups = complete("rec { alpha = 1; beta = <caret>; }");
        assertContainsElements(lookups, "alpha", "beta");
    }

    public void testNonRecursiveSetIsNotInScope() {
        List<String> lookups = complete("{ alpha = 1; beta = <caret>; }");
        assertDoesntContain(lookups, "alpha", "beta");
    }

    public void testInheritWithoutSourceIsNotDeclared() {
        List<String> lookups = complete("let inherit (x) alpha; inherit beta; in <caret>");
        assertContainsElements(lookups, "alpha");
        assertDoesntContain(lookups, "beta");
    }

    public void testOutOfScopeVariables() {
        List<String> lookups = complete("[ (let alpha = 1; in alpha) (let beta = 1; in <caret>) ]");
        assertContainsElements(lookups, "beta");
        assertDoesntContain(lookups, "alpha");
    }

    public void testGlobalBuiltins() {
        List<String> lookups = complete("<caret>");
        assertContainsElements(lookups, "builtins", "import", "true", "map", "toString");
        // Only available via builtins.<name>
        assertDoesntContain(lookups, "attrNames", "concatStringsSep");
    }

    public void testLocalVariablesBeforeBuiltins() {
        List<String> lookups = complete("let mapper = 1; in ma<caret>");
        assertEquals(List.of("mapper", "map"), lookups);
    }

    public void testShadowedBuiltinIsListedOnce() {
        List<String> lookups = complete("let map = 1; in <caret>");
        assertEquals(1, lookups.stream().filter("map"::equals).count());
        assertEquals("map", lookups.get(0));
    }

    public void testInsertSingleCandidate() {
        myFixture.configureByText(NixFileType.INSTANCE, "let someVariable = 1; in someV<caret>");
        assertNull(myFixture.completeBasic());
        myFixture.checkResult("let someVariable = 1; in someVariable<caret>");
    }

    public void testNoCompletionInAttributeName() {
        List<String> lookups = complete("let alpha = 1; in { al<caret> = 1; }");
        assertDoesntContain(lookups, "alpha");
    }

    private List<String> complete(String code) {
        myFixture.configureByText(NixFileType.INSTANCE, code);
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        assertNotNull("Expected a lookup with multiple candidates", lookups);
        return lookups;
    }
}
