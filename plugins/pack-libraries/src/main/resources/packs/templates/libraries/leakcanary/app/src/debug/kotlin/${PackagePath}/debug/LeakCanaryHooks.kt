package ${Package}.debug

import leakcanary.AppWatcher

object LeakCanaryHooks {
    fun watchingEnabled(): Boolean = AppWatcher.objectWatcher.toString().isNotBlank()
}
