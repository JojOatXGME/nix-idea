package org.nixos.idea.inspections

import org.junit.jupiter.api.Test

class NixUnusedVariableInspectionTest : AbstractInspectionTest(NixUnusedVariableInspection()) {

    @Test
    fun let() = check(
        """
        let
          used = 1;
          <warning descr="Unused variable 'unused'">unused</warning> = 2;
        in
        used
        """.trimIndent(),
        fixed = """
        let
          used = 1;
        in
        used
        """.trimIndent(),
    )

    @Test
    fun `let with attribute paths`() = check(
        """let <warning descr="Unused variable 'a'">a</warning>.b = 1; <warning descr="Unused variable 'a'">a</warning>.c = 2; in 3""",
        fixed = """let in 3""",
    )

    @Test
    fun `let with inherit`() = check(
        """let inherit (x) used <warning descr="Unused variable 'unused'">unused</warning>; inherit (x) <warning descr="Unused variable 'other'">other</warning>; in used""",
        fixed = """let inherit (x) used; in used""",
    )

    @Test
    fun parameters() = check(
        """<warning descr="Unused variable 'x'">x</warning>: { a, ... }@<warning descr="Unused variable 'args'">args</warning>: 1""",
    )

    @Test
    fun `formals when enabled`() {
        val inspection = NixUnusedVariableInspection()
        inspection.reportFormals = true
        fixture.enableInspections(inspection)
        check("""{ <warning descr="Unused variable 'a'">a</warning>, b }: b""")
    }

    @Test
    fun `used in nested scopes`() = check(
        """let a = 1; b = 2; c = 3; in x: { inherit a; y = "${'$'}{b}"; z = with x; c; }""",
    )

    @Test
    fun `used by other bindings and defaults`() = check(
        """let a = 1; b = a; in { c ? b, d ? c }: d""",
    )

    @Test
    fun `ignores underscore`() = check(
        """_: let _unused = 1; in 2""",
    )

    @Test
    fun `ignores recursive sets and legacy let`() = check(
        """[ rec { a = 1; } (let { a = 1; body = 2; }) ]""",
    )

    @Test
    fun `shadowed variable is unused`() = check(
        """let <warning descr="Unused variable 'a'">a</warning> = 1; in let a = 2; in a""",
        fixed = """let in let a = 2; in a""",
    )
}
