plugins {
    `kotlin-dsl`
}

group = "${Package}.buildlogic"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation(kotlin("gradle-plugin", version = "2.2.21"))
}

gradlePlugin {
    plugins {
        register("wizardAndroidApplication") {
            id = "wizard.android.application"
            implementationClass = "wizard.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("wizardAndroidLibrary") {
            id = "wizard.android.library"
            implementationClass = "wizard.buildlogic.AndroidLibraryConventionPlugin"
        }
    }
}
