plugins {
    base
}

// build-logic is an included build (settings.gradle.kts), not a subproject, so its `clean`
// isn't reached by unqualified `./gradlew clean` unless we wire it in here explicitly.
tasks.named("clean") {
    dependsOn(gradle.includedBuild("build-logic").task(":clean"))
}
