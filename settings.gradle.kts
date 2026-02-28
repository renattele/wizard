rootProject.name = "Wizard"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":contracts:core")
include(":contracts:manifest")
include(":engine:security")
include(":engine:catalog")
include(":engine:resolver")
include(":engine:generator")
include(":plugins:pack-core")
include(":plugins:pack-android")
include(":plugins:pack-compose")
include(":plugins:pack-arch")
include(":server:api")
include(":web:app")
