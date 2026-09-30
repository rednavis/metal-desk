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
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.mongodb.reactive)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.reactor.test)
}
