plugins {
    id("metaldesk.spring-boot-conventions")
    id("metaldesk.quality-conventions")
}

base {
    archivesName.set("metal-api")
}

dependencies {
    implementation(project(":libs:share"))
    implementation(project(":libs:payments"))
    implementation(project(":libs:mail"))
    implementation(project(":libs:persistence"))
    implementation(project(":libs:migrations"))
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.mongodb.reactive)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)

    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(testFixtures(project(":libs:persistence")))
    testImplementation(libs.reactor.test)
    testImplementation(libs.wiremock.standalone)
}

// The hand-off end-to-end test (T-041) runs the real apps/admin application next to this one, in a
// class loader of its own, because the two cannot share a classpath: admin is MVC with the blocking
// MongoDB driver, this service is WebFlux with the reactive one. Only its runtime classpath is
// resolved here, and it is handed to the test as a system property, so nothing of admin reaches
// this module's own classpath.
val adminRuntime = configurations.create("adminRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    adminRuntime(project(":apps:admin"))
}

// The classpath is handed over through a CommandLineArgumentProvider that captures only a file
// collection, not the script object, so the task stays compatible with the configuration cache.
tasks.test {
    val adminClasspath: FileCollection = adminRuntime
    inputs.files(adminClasspath)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dmetaldesk.admin.classpath=" +
                    adminClasspath.files.joinToString(File.pathSeparator) { it.absolutePath }
            )
        }
    )
}
