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
