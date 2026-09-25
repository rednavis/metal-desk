rootProject.name = "metal-desk"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// A settings-level plugin can't reference the version catalog it will itself help resolve, so this
// one version is pinned here as the sole exception to "the catalog is the single version source."
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

includeBuild("build-logic")

include(
    "libs:share",
    "libs:payments",
    "libs:mail",
    "services:api",
    "services:pricing-bridge",
    "apps:admin",
)
