package ru.renattele.wizard.manifest

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatibilityContractTest {
    @Test
    fun `default contract is aligned to api v1`() {
        val contract = CompatibilityContract()

        assertEquals("1.x", contract.apiRange)
        assertEquals("1.x", contract.engineRange)
    }
}
