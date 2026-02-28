package ru.renattele.wizard.contracts.v1

import kotlin.test.Test
import kotlin.test.assertEquals

class WizardLockfileContractTest {
    @Test
    fun `lockfile stores strict mode by default`() {
        val lockfile = WizardLockfile(
            templateId = "android-app",
            resolutionHash = "abc",
        )

        assertEquals(true, lockfile.strictMode)
    }
}
