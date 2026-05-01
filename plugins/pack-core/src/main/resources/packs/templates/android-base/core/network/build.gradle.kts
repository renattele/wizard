plugins {
    id("wizard.android.library")
    // __CORE_NETWORK_PLUGIN_MARKER__
}

android {
    namespace = "${Package}.core.network"
}

dependencies {
    implementation(project(":core:common"))
    // __CORE_NETWORK_DEPENDENCY_MARKER__
}
