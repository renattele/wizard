package ${Package}.core.testing

import io.mockk.mockkClass

object MockkSupport {
    fun <T : Any> create(type: Class<T>): T = mockkClass(type)
}
