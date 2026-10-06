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

// Local build cache only (under the Gradle user home, `~/.gradle/caches/build-cache-1`). There is no
// remote cache node for this repository, so none is configured: CI persists that same local cache
// through the GitHub Actions cache (gradle/actions/setup-gradle), read-only except on master — see
// .github/workflows/README.md. A true remote node (HttpBuildCache) is deferred.
buildCache {
    local {
        isEnabled = true
    }
}

includeBuild("build-logic")

include(
    "libs:share",
    "libs:payments",
    "libs:mail",
    "libs:persistence",
    "libs:migrations",
    "services:api",
    "services:pricing-bridge",
    "apps:admin",
)
