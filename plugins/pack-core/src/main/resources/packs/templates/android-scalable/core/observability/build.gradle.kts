plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.core.observability"
}

dependencies {
    implementation(project(":core:common"))
}
