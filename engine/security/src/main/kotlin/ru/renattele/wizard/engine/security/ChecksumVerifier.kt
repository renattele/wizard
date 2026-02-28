package ru.renattele.wizard.engine.security

import java.security.MessageDigest

interface ChecksumVerifier {
    fun verifySha256(content: ByteArray, expectedSha256: String): Boolean
    fun sha256(content: ByteArray): String
}

class Sha256ChecksumVerifier : ChecksumVerifier {
    override fun verifySha256(content: ByteArray, expectedSha256: String): Boolean {
        val actual = sha256(content)
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    override fun sha256(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
