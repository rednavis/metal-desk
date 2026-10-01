plugins {
    id("metaldesk.spring-boot-conventions")
    id("metaldesk.quality-conventions")
}

base {
    archivesName.set("metal-admin")
}

// MVC on virtual threads, deliberately not WebFlux (ADR-0004): this module uses the blocking MongoDB
// driver and blocking repositories, and must never have WebFlux or the reactive-streams driver on
// its classpath (asserted in AdminClasspathTest).
dependencies {
    implementation(project(":libs:share"))
    implementation(project(":libs:persistence"))
    implementation(project(":libs:mail"))
    implementation(project(":libs:migrations"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)

    testImplementation(testFixtures(project(":libs:persistence")))
    testImplementation(libs.spring.boot.starter.webmvc.test)
}
