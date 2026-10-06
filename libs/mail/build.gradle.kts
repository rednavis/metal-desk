plugins {
    id("metaldesk.java-conventions")
    id("metaldesk.quality-conventions")
    `java-library`
}

base {
    archivesName.set("metal-mail")
}

dependencies {
    // EmailAddress lives in libs/share, and it is part of MailSender's signature.
    api(project(":libs:share"))

    // Mono is part of MailSender's signature, so reactor-core is api. Its version comes from the
    // Spring Boot BOM (the catalog alias carries none), the same source the services use; the BOM is
    // imported for version alignment only and nothing Spring is added to the classpath.
    implementation(platform(libs.spring.boot.dependencies))
    api(libs.reactor.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.reactor.test)
}
