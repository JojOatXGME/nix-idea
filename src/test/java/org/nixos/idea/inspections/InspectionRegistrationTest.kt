package org.nixos.idea.inspections

import com.intellij.codeInspection.LocalInspectionEP
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nixos.idea._testutil.WithIdeaPlatform

@WithIdeaPlatform
class InspectionRegistrationTest {

    private val nixInspections = LocalInspectionEP.LOCAL_INSPECTION.extensionList.filter { it.language == "Nix" }

    @Test
    fun `inspections are registered`() {
        assertTrue(nixInspections.isNotEmpty())
    }

    @Test
    fun `inspections have descriptions`() {
        for (ep in nixInspections) {
            val inspection = ep.instantiateTool()
            assertEquals(inspection.javaClass.simpleName.removeSuffix("Inspection"), ep.getShortName())
            assertNotNull(inspection.javaClass.getResource("/inspectionDescriptions/${ep.getShortName()}.html"),
                "Missing description for ${ep.getShortName()}")
        }
    }
}
