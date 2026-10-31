package org.nixos.idea.inspections

import org.junit.jupiter.api.Test

class NixUnquotedUriInspectionTest : AbstractInspectionTest(NixUnquotedUriInspection()) {

    @Test
    fun simple() = check(
        """fetchTarball <warning descr="Unquoted URLs are deprecated">https://example.com/a.tar.gz?x=1&y=${'$'}</warning>""",
        fixed = """fetchTarball "https://example.com/a.tar.gz?x=1&y=${'$'}"""",
    )

    @Test
    fun `quoted URL`() = check(
        """fetchTarball "https://example.com/a.tar.gz"""",
    )
}
