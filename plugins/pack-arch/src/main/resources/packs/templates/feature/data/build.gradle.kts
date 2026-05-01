plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.feature.${FeaturePackage}.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":feature:${FeaturePackage}:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
}
