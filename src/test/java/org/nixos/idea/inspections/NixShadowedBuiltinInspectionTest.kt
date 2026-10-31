package org.nixos.idea.inspections

import org.junit.jupiter.api.Test

class NixShadowedBuiltinInspectionTest : AbstractInspectionTest(NixShadowedBuiltinInspection()) {

    @Test
    fun let() = check(
        """let <warning descr="Variable 'map' shadows a built-in">map</warning> = f: f; in map""",
    )

    @Test
    fun parameters() = check(
        """<warning descr="Variable 'import' shadows a built-in">import</warning>: { <warning descr="Variable 'true' shadows a built-in">true</warning>, other }: 1""",
    )

    @Test
    fun `recursive set`() = check(
        """rec { <warning descr="Variable 'null' shadows a built-in">null</warning> = 1; x = 2; }""",
    )

    @Test
    fun `non-recursive set and builtins without global name`() = check(
        """let add = 1; in { map = 1; }""",
    )

    @Test
    fun `inherit without source does not shadow`() = check(
        """let inherit map; in map""",
    )
}
