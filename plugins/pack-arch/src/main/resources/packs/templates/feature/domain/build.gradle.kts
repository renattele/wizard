plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.feature.${FeaturePackage}.domain"
}

dependencies {
    implementation(project(":core:common"))
}
