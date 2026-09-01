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

    // Mockito's inline mock maker needs an instrumentation agent. Left alone it attaches one to the
    // running JVM, which the JDK warns about and will stop allowing; Mockito's documented fix is to
    // start the test JVM with mockito-core itself as the agent. The jar is taken from the test
    // classpath (Spring modules get it, at the Spring-managed version, from spring-boot-starter-test),
    // so there is no second version to keep in step, and a module without Mockito gets no flag.
    //
    // Netty (reactive Mongo driver, WebFlux) loads a native library through a restricted JDK method,
    // which the JDK warns about on every run and will block unless native access is granted. The test
    // JVM grants it to classpath code explicitly.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val mockitoCore = classpath.filter { it.name.startsWith("mockito-core-") }
    doFirst {
        mockitoCore.files.firstOrNull()?.let { jvmArgs("-javaagent:${it.absolutePath}") }
    }
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // Gradle 9 no longer supplies the JUnit Platform launcher itself. The Spring modules get it
    // through spring-boot-starter-test, but a module with no Spring would otherwise fail to start
    // its tests at all, so it is declared here for every module rather than in each build file (the
    // version comes from the junit-bom above). Without this a newly added module could not even run
    // the architecture test that the quality conventions apply to it.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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
