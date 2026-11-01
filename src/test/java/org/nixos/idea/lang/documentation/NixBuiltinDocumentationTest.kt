package org.nixos.idea.lang.documentation

import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nixos.idea._testutil.WithIdeaPlatform
import org.nixos.idea.file.NixFileType
import org.nixos.idea.settings.NixSymbolSettings

@WithIdeaPlatform.OnEdt
@WithIdeaPlatform.CodeInsight
@Suppress("UnstableApiUsage")
class NixBuiltinDocumentationTest {

    private lateinit var myFixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp(fixture: CodeInsightTestFixture) {
        myFixture = fixture
        NixSymbolSettings.getInstance().enabled = true
    }

    @Test
    fun global_builtin() {
        val doc = documentationAtCaret("m<caret>ap (x: x) [ ]")
        assertTrue(doc.contains("builtins.map f list"), doc)
        assertTrue(doc.contains("Apply the function"), doc)
        assertTrue(doc.contains("without <code>builtins.</code> prefix"), doc)
    }

    @Test
    fun builtins_attribute() {
        val doc = documentationAtCaret("builtins.currentSys<caret>tem")
        assertTrue(doc.contains("builtins.currentSystem"), doc)
        assertTrue(doc.contains("https://nix.dev/manual/nix/stable/"), doc)
        assertTrue(!doc.contains("@docroot@"), doc)
    }

    @Test
    fun experimental_builtin() {
        val doc = documentationAtCaret("builtins.getFl<caret>ake \"nixpkgs\"")
        assertTrue(doc.contains("<code>flakes</code>"), doc)
    }

    @Test
    fun shadowed_builtin() {
        myFixture.configureByText(NixFileType.INSTANCE, "let map = 1; in m<caret>ap")
        assertEquals(listOf<DocumentationTarget>(), builtinTargetsAtCaret())
    }

    @Test
    fun shadowed_builtins_attribute() {
        myFixture.configureByText(NixFileType.INSTANCE, "let builtins = { }; in builtins.m<caret>ap")
        assertEquals(listOf<DocumentationTarget>(), builtinTargetsAtCaret())
    }

    private fun documentationAtCaret(code: String): String {
        myFixture.configureByText(NixFileType.INSTANCE, code)
        val targets = builtinTargetsAtCaret()
        assertEquals(1, targets.size, targets.toString())
        val result = targets[0].computeDocumentation() as DocumentationData
        return result.html
    }

    private fun builtinTargetsAtCaret(): List<DocumentationTarget> {
        return IdeDocumentationTargetProvider.getInstance(myFixture.project)
            .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)
            .filterIsInstance<NixBuiltinDocumentationTarget>()
    }
}
