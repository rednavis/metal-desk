plugins {
    id("metaldesk.java-conventions")
    id("metaldesk.quality-conventions")
    `java-library`
}

base {
    archivesName.set("metal-payments")
}

dependencies {
    api(project(":libs:share"))

    // Mono is part of PaymentProvider's signature, so reactor-core is api, not implementation. Its
    // version comes from the Spring Boot BOM (the catalog alias carries none), the same source the
    // services use, so this library and its consumers cannot disagree on a Reactor version. The BOM
    // is imported only for version alignment; nothing Spring is added to the classpath.
    implementation(platform(libs.spring.boot.dependencies))
    api(libs.reactor.core)

    testImplementation(libs.reactor.test)
    // Gradle 9 no longer supplies the JUnit Platform launcher itself; see libs/share.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
