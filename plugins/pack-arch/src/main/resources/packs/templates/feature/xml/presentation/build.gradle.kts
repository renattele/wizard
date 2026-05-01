plugins {
    id("wizard.android.library")
}

android {
    namespace = "${Package}.feature.${FeaturePackage}.presentation"
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:${FeaturePackage}:domain"))
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.material)
}
