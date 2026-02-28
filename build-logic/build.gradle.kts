plugins {
    `kotlin-dsl`
}

group = "ru.renattele.wizard.buildlogic"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
}

gradlePlugin {
    plugins {
        register("wizardKotlinJvmConventions") {
            id = "wizard.kotlin-jvm-conventions"
            implementationClass = "ru.renattele.wizard.buildlogic.WizardKotlinJvmConventionsPlugin"
        }
        register("wizardKotlinMultiplatformConventions") {
            id = "wizard.kotlin-multiplatform-conventions"
            implementationClass = "ru.renattele.wizard.buildlogic.WizardKotlinMultiplatformConventionsPlugin"
        }
    }
}
