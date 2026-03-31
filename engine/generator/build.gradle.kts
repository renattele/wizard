plugins {
    id("wizard.kotlin-jvm-conventions")
}

dependencies {
    implementation(projects.contracts.core)
    implementation(projects.engine.configuration)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
}
