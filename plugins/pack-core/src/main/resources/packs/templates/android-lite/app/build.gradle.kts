plugins {
    id("wizard.android.application")
    // __APP_PLUGIN_MARKER__
}

android {
    namespace = "${Package}"
    defaultConfig {
        applicationId = "${Package}"
        minSdk = ${MinSdk}
        targetSdk = ${TargetSdk}
        versionCode = 1
        versionName = "1.0.0"
    }

    // __APP_ANDROID_BUILD_FEATURES__
    // __APP_ANDROID_COMPOSE_OPTIONS__
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
${AppFeaturePresentationDependencies}
${AppFeatureDomainDependencies}
${AppFeatureDataDependencies}
${AppSharedModuleDependencies}
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    // __APP_DEPENDENCY_MARKER__
}
