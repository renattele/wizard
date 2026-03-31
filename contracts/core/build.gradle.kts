plugins {
    id("wizard.kotlin-multiplatform-conventions")
}

kotlin {
    jvm()
    js {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
