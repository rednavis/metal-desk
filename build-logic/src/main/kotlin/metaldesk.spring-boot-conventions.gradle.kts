import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("metaldesk.java-conventions")
    id("org.springframework.boot")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    "implementation"(platform(libs.findLibrary("spring-boot-dependencies").get()))
    "implementation"(libs.findLibrary("spring-boot-starter").get())
    "testImplementation"(libs.findLibrary("spring-boot-starter-test").get())
}
