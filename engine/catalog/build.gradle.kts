plugins {
    id("wizard.kotlin-jvm-conventions")
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    implementation(projects.contracts.core)
    implementation(projects.contracts.manifest)
    implementation(projects.engine.security)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
}
