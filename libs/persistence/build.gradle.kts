plugins {
    id("metaldesk.java-conventions")
    id("metaldesk.quality-conventions")
    `java-library`
    `java-test-fixtures`
}

base {
    archivesName.set("metal-persistence")
}

dependencies {
    api(project(":libs:share"))

    // The documents and mappers are the storage *shape* shared by services/api (reactive) and
    // apps/admin (blocking), so this module depends on Spring Data MongoDB's mapping annotations
    // only. Neither Mongo driver nor any repository lives here: each app brings the driver and the
    // repository style (Mono/Flux or blocking) that fits its execution model. The version comes from
    // the Spring Boot BOM, as in libs/mail.
    implementation(platform(libs.spring.boot.dependencies))
    api(libs.spring.data.mongodb)
    // @EntityScan, so the documents in this library are discovered (and their indexes declared) even
    // though they are outside the application's own package.
    implementation(libs.spring.boot.persistence)

    // The one real-MongoDB harness, shared by every module that tests against storage
    // (services/api, apps/admin) so each JVM starts a single container.
    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesApi(project(":libs:share"))
    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(platform(libs.testcontainers.bom))
    testFixturesApi(libs.testcontainers.mongodb)
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi("org.junit.jupiter:junit-jupiter-api")
}
