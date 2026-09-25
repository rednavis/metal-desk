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
}
