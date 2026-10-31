package org.nixos.idea.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.nixos.idea._testutil.WithIdeaPlatform
import org.nixos.idea.file.NixFileType
import org.nixos.idea.settings.NixSymbolSettings

/**
 * Base class for tests of inspections.
 *
 * ```
 * class NixUnquotedUriInspectionTest : AbstractInspectionTest(NixUnquotedUriInspection()) {
 *     @Test
 *     fun simple() = check(
 *         """fetchTarball <warning descr="Unquoted URLs are deprecated">https://example.com</warning>""",
 *         fixed = """fetchTarball "https://example.com""""
 *     )
 * }
 * ```
 */
@WithIdeaPlatform.OnEdt
@WithIdeaPlatform.CodeInsight
abstract class AbstractInspectionTest(private val inspection: LocalInspectionTool) {

    protected lateinit var fixture: CodeInsightTestFixture
    private var symbolSettingsBackup = false

    @BeforeEach
    fun setUpInspection(fixture: CodeInsightTestFixture) {
        this.fixture = fixture
        symbolSettingsBackup = NixSymbolSettings.getInstance().enabled
        NixSymbolSettings.getInstance().enabled = true
        fixture.enableInspections(inspection)
    }

    @AfterEach
    fun tearDownInspection() {
        NixSymbolSettings.getInstance().enabled = symbolSettingsBackup
    }

    /**
     * Verifies the problems reported by the inspection,
     * and optionally the result of applying all quick fixes.
     *
     * @param code  The code to test. Expected problems are marked with tags like
     *              `<warning descr="...">...</warning>` or `<weak_warning descr="...">...</weak_warning>`.
     *              See [CodeInsightTestFixture.checkHighlighting] for more details.
     * @param fixed The expected code after applying all quick fixes, one after another.
     *              If `null`, quick fixes are not tested.
     */
    fun check(@Language("Nix") code: String, @Language("Nix") fixed: String? = null) {
        fixture.configureByText(NixFileType.INSTANCE, code)
        fixture.checkHighlighting(true, false, true)
        if (fixed != null) {
            repeat(MAX_FIXES) {
                val fix = fixture.getAllQuickFixes().firstOrNull()
                if (fix == null) {
                    assertEquals(fixed, fixture.file.text)
                    return
                }
                fixture.launchAction(fix)
            }
            fail<Unit>("Still got quick fixes after applying $MAX_FIXES fixes")
        }
    }

    private companion object {
        const val MAX_FIXES = 100
    }
}
