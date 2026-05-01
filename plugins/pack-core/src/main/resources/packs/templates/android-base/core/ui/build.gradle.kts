plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.core.ui"
}

dependencies {
    implementation(project(":core:common"))
    // __CORE_UI_DEPENDENCY_MARKER__
}
