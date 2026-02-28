plugins {
    id("wizard.kotlin-multiplatform-conventions")
}

kotlin {
    js {
        browser {
            binaries.executable()
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
    }

    sourceSets {
        jsMain.dependencies {
            implementation(projects.contracts.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
