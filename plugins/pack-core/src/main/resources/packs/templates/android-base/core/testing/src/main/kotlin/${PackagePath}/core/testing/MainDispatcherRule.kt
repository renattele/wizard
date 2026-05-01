package ${Package}.core.testing

import org.junit.rules.TestWatcher
import org.junit.runner.Description

class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Unit
}
