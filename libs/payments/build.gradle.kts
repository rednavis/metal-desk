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
    // Security floor over the BOM's jackson (see jacksonFloor in the version catalog).
    implementation(platform(libs.jackson.bom.floor))
    api(libs.reactor.core)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(libs.reactor.test)
    testImplementation(libs.wiremock.standalone)
}
