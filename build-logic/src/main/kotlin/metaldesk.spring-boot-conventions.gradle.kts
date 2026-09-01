import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("metaldesk.java-conventions")
    id("org.springframework.boot")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    "implementation"(platform(libs.findLibrary("spring-boot-dependencies").get()))
    // Floors over Boot's BOM for advisories it has not picked up yet (see jacksonFloor in the catalog). A
    // platform resolves to the highest version of any BOM, so this only ever raises jackson.
    "implementation"(platform(libs.findLibrary("jackson-bom-floor").get()))
    constraints {
        for (name in listOf("tomcat-embed-core-floor", "tomcat-embed-el-floor", "tomcat-embed-websocket-floor")) {
            "implementation"(libs.findLibrary(name).get()) {
                because("Boot 4.1.1 manages Tomcat 11.0.24, which has high-severity advisories")
            }
        }
    }
    "implementation"(libs.findLibrary("spring-boot-starter").get())
    "testImplementation"(libs.findLibrary("spring-boot-starter-test").get())
}
