rootProject.name = "${ProjectName}"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":app",
    ":core:common",
    ":core:ui",
    ":core:designsystem",
    ":core:network",
    ":core:database",
    ":core:testing",
)
${FeatureIncludes}
