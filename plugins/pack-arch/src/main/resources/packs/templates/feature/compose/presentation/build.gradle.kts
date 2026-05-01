plugins {
    id("wizard.android.library")
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "${Package}.feature.${FeaturePackage}.presentation"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:${FeaturePackage}:domain"))
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
