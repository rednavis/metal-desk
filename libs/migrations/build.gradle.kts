plugins {
    id("metaldesk.java-conventions")
    id("metaldesk.quality-conventions")
    `java-library`
}

base {
    archivesName.set("metal-migrations")
}

// The database migrations of the whole platform, in one place, so services/api and apps/admin see the
// same set and both run it at startup (ADR-0006). Mongock runs the change units through the plain
// synchronous driver on a connection of its own, opened for the run and closed after it, so this
// module is the same for the reactive service and the blocking app and neither has to bring the other's
// driver into its execution model. Versions of the Spring and driver artifacts come from the Spring
// Boot BOM; Mongock's are in the catalog.
dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter)
    implementation(libs.mongock.standalone)
    implementation(libs.mongock.driver.sync)
    implementation(libs.mongodb.driver.sync)
    implementation(libs.spring.security.crypto)

    testImplementation(testFixtures(project(":libs:persistence")))
}
