package ru.renattele.wizard.web

import kotlin.test.Test
import kotlin.test.assertEquals

class ShareCodecTest {
    @Test
    fun `base64url codec round trips json payload`() {
        val payload = """{"templateId":"android-app","featureNames":["home","catalog"]}"""
        val encoded = base64UrlEncode(payload)
        val decoded = base64UrlDecode(encoded)

        assertEquals(payload, decoded)
    }
}
