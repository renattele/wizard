plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.core.testing"
}

dependencies {
    implementation(libs.junit4)
    implementation(libs.androidx.test.ext)
    // __CORE_TESTING_DEPENDENCY_MARKER__
}
