plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.core.designsystem"
}

dependencies {
    implementation(project(":core:common"))
    // __CORE_DESIGNSYSTEM_DEPENDENCY_MARKER__
}
