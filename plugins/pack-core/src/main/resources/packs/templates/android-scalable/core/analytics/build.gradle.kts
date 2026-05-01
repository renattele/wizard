plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.core.analytics"
}

dependencies {
    implementation(project(":core:common"))
}
