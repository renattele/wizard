plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy {
            activateDependencyLocking()
        }
    }
}

val verifyGovernance by tasks.registering {
    notCompatibleWithConfigurationCache("Scans repository files for governance artifacts")
    group = "verification"
    description = "Ensures strict governance artifacts are present"

    val metadata = rootProject.file("gradle/verification-metadata.xml")

    doLast {
        require(metadata.exists()) {
            "Missing gradle/verification-metadata.xml. Generate with ./gradlew --write-verification-metadata sha256 help"
        }

        val lockFiles = rootProject.fileTree(rootProject.projectDir) {
            include("**/gradle.lockfile")
            exclude("**/build/**")
            exclude(".gradle/**")
        }.files

        require(lockFiles.isNotEmpty()) {
            "No dependency lockfiles found. Generate with ./gradlew --write-locks build"
        }
    }
}

tasks.register("compatibilityCheck") {
    group = "verification"
    description = "Runs compile/test/governance checks for compatibility contracts"
    dependsOn(
        ":contracts:core:check",
        ":contracts:manifest:check",
        ":engine:configuration:test",
        ":engine:resolver:test",
        ":engine:generator:test",
        ":engine:catalog:test",
        ":engine:security:test",
        verifyGovernance,
    )
}

tasks.register("lockfileCheck") {
    group = "verification"
    description = "Verifies lockfiles and dependency verification metadata are configured"
    dependsOn(verifyGovernance)
}
