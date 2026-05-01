plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.core.sync"
}

dependencies {
    implementation(project(":core:common"))
}
