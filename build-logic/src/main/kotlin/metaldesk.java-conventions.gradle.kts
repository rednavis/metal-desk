import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
}

// Precompiled script plugins don't get the type-safe `libs` accessor (that's only generated for
// build-logic's own build.gradle.kts) — fetch the shared catalog manually instead.
val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

group = "com.rednavis.metaldesk"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion.toInt()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-serial,-processing"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // Gradle never resolves annotationProcessor transitively (by design — see
    // https://github.com/gradle/gradle/issues/2510), so a dependency's compileOnlyApi/api Lombok
    // exposure alone isn't enough: every module that wants to actually use Lombok annotations
    // (e.g. @Slf4j) needs its own annotation-processing step wired up too. Doing it once here,
    // since every Java module already applies this convention plugin, means libs/share can expose
    // Lombok on its consumers' compile classpath (see its build.gradle.kts) and those consumers'
    // @Slf4j etc. just work without each of them repeating this.
    annotationProcessor(libs.findLibrary("lombok").get())
    testAnnotationProcessor(libs.findLibrary("lombok").get())
}
