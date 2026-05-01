plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)
}
