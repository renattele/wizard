plugins {
    id("wizard.kotlin-jvm-conventions")
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("ru.renattele.wizard.server.ApplicationKt")
}

dependencies {
    implementation(projects.contracts.core)
    implementation(projects.contracts.manifest)
    implementation(projects.engine.catalog)
    implementation(projects.engine.generator)
    implementation(projects.engine.resolver)
    implementation(projects.engine.security)
    implementation(projects.plugins.packCore)
    implementation(projects.plugins.packAndroid)
    implementation(projects.plugins.packCompose)
    implementation(projects.plugins.packArch)

    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverDefaultHeaders)
    implementation(libs.ktor.serverAutoHead)
    implementation(libs.ktor.serverCompression)
    implementation(libs.ktor.serverCallId)
    implementation(libs.ktor.serverRequestValidation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
}
