plugins {
    id("metaldesk.spring-boot-conventions")
    id("metaldesk.quality-conventions")
}

base {
    archivesName.set("metal-admin")
}

dependencies {
    implementation(project(":libs:share"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
}
