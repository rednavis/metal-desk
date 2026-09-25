plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Plugin-marker-artifact technique: lets a precompiled script plugin `plugins { id(...) }` on a
    // plugin whose version is declared once, in the shared version catalog.
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.spotbugs.gradle.plugin)
}
