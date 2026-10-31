package org.nixos.idea.inspections

import org.junit.jupiter.api.Test

class NixShadowedVariableInspectionTest : AbstractInspectionTest(NixShadowedVariableInspection()) {

    @Test
    fun simple() = check(
        """x: let <weak_warning descr="Variable 'x' shadows another variable">x</weak_warning> = 1; in x""",
    )

    @Test
    fun `nested functions`() = check(
        """{ a, b }: { <weak_warning descr="Variable 'b' shadows another variable">b</weak_warning>, c }: a""",
    )

    @Test
    fun `does not report builtins`() = check(
        """let map = 1; in map""",
    )

    @Test
    fun `does not report siblings`() = check(
        """[ (x: x) (x: x) ]""",
    )

    @Test
    fun `inherit without source does not shadow`() = check(
        """x: let inherit x; in x""",
    )
}
