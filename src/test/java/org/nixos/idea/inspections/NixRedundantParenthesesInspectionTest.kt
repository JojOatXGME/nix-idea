package org.nixos.idea.inspections

import org.junit.jupiter.api.Test

class NixRedundantParenthesesInspectionTest : AbstractInspectionTest(NixRedundantParenthesesInspection()) {

    @Test
    fun `around simple expressions`() = check(
        """[ <weak_warning descr="Redundant parentheses">(x)</weak_warning> <weak_warning descr="Redundant parentheses">("s")</weak_warning> <weak_warning descr="Redundant parentheses">(a.b)</weak_warning> ]""",
        fixed = """[ x "s" a.b ]""",
    )

    @Test
    fun `in places accepting any expression`() = check(
        """
        let x = <weak_warning descr="Redundant parentheses">(a + b)</weak_warning>; in
        if <weak_warning descr="Redundant parentheses">(a == b)</weak_warning> then <weak_warning descr="Redundant parentheses">(y: y)</weak_warning> else "${'$'}{<weak_warning descr="Redundant parentheses">(f x)</weak_warning>}"
        """.trimIndent(),
        fixed = """
        let x = a + b; in
        if a == b then y: y else "${'$'}{f x}"
        """.trimIndent(),
    )

    @Test
    fun `nested parentheses`() = check(
        """f <weak_warning descr="Redundant parentheses">(<weak_warning descr="Redundant parentheses">(g x)</weak_warning>)</weak_warning>""",
        fixed = """f (g x)""",
    )

    @Test
    fun `required parentheses`() = check(
        """[ (f x) (a + b) (-1) ((a: a) 1) (a.b or c).d (a.b) or c (x: x) ]""",
    )

    @Test
    fun `select with default`() = check(
        """[ (a.b).c or d ]""",
    )

    @Test
    fun `adds whitespace when necessary`() = check(
        """f<weak_warning descr="Redundant parentheses">(x)</weak_warning>""",
        fixed = """f x""",
    )

    @Test
    fun `ignores parentheses with comments`() = check(
        """f (/* comment */ x)""",
    )
}
