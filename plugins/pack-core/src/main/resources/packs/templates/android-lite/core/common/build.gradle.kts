plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)
    // __CORE_COMMON_DEPENDENCY_MARKER__
}
