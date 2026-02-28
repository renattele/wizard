package ru.renattele.wizard.engine.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sha256ChecksumVerifierTest {
    private val verifier = Sha256ChecksumVerifier()

    @Test
    fun `verifies matching sha256 hash`() {
        val payload = "wizard".encodeToByteArray()
        val expected = verifier.sha256(payload)

        assertTrue(verifier.verifySha256(payload, expected))
    }

    @Test
    fun `rejects mismatched sha256 hash`() {
        val payload = "wizard".encodeToByteArray()

        assertFalse(verifier.verifySha256(payload, "abcd"))
    }
}
