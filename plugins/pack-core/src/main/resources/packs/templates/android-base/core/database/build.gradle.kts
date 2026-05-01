plugins {
    id("wizard.android.library")
    // __CORE_DATABASE_PLUGIN_MARKER__
}

android {
    namespace = "${Package}.core.database"
}

dependencies {
    implementation(project(":core:common"))
    // __CORE_DATABASE_DEPENDENCY_MARKER__
}
