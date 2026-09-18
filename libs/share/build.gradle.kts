plugins {
    id("metaldesk.java-conventions")
    id("metaldesk.quality-conventions")
    `java-library`
}

base {
    archivesName.set("metal-share")
}

dependencies {
    // compileOnlyApi (not api): Lombok is a compile-/annotation-processing-time-only tool, never
    // needed at runtime, so this must not leak onto anyone's runtime classpath — but it does need
    // to reach every module that depends on libs/share (apps/admin, services/api,
    // services/pricing-bridge) on their *compile* classpath, so they can use e.g. @Slf4j without
    // declaring the dependency themselves. The annotation-processing step itself is wired up in
    // metaldesk.java-conventions.gradle.kts — see the comment there for why that can't live here.
    compileOnlyApi(libs.lombok)
}
