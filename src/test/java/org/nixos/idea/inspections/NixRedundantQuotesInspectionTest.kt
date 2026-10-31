package org.nixos.idea.inspections

import org.junit.jupiter.api.Test

class NixRedundantQuotesInspectionTest : AbstractInspectionTest(NixRedundantQuotesInspection()) {

    @Test
    fun bindings() = check(
        """{ <weak_warning descr="Redundant quotes">"a"</weak_warning> = 1; b.<weak_warning descr="Redundant quotes">"c-d'"</weak_warning> = 2; }""",
        fixed = """{ a = 1; b.c-d' = 2; }""",
    )

    @Test
    fun `select and has`() = check(
        """[ x.<weak_warning descr="Redundant quotes">"a"</weak_warning> (x ? <weak_warning descr="Redundant quotes">"b"</weak_warning>) ]""",
        fixed = """[ x.a (x ? b) ]""",
    )

    @Test
    fun inherit() = check(
        """{ inherit (x) <weak_warning descr="Redundant quotes">"a"</weak_warning>; }""",
        fixed = """{ inherit (x) a; }""",
    )

    @Test
    fun `required quotes`() = check(
        """{ "a b" = 1; "1a" = 2; "" = 3; "if" = 4; "or" = 5; "a${'$'}{x}" = 6; "a.b" = 7; ${'$'}{"interpolation"} = 8; }""",
    )
}
