plugins {
    id("wizard.kotlin-jvm-conventions")
}

dependencies {
    implementation(projects.contracts.core)
    implementation(projects.contracts.manifest)
    implementation(projects.engine.catalog)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
}
