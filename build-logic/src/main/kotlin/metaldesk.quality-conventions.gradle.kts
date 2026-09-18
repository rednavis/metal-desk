import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
    checkstyle
    jacoco
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

configure<SpotlessExtension> {
    java {
        googleJavaFormat()
        target("src/*/java/**/*.java")
    }
}

val checkstyleConfigDir = rootProject.layout.projectDirectory.dir("config/checkstyle")

// Equivalent of the team's former maven-checkstyle-plugin <configuration> block:
//   skip                        -> not needed; this convention plugin is only applied where wanted
//   consoleOutput=true          -> Checkstyle.showViolations (below)
//   failsOnError / failOnViolation -> Checkstyle.ignoreFailures = false (below; also Gradle's default)
//   excludeGeneratedSources     -> N/A: Gradle source sets never include generated-sources dirs here
//   includeTestSourceDirectory  -> N/A: Gradle's checkstyle plugin registers a task per source set
//                                  (checkstyleMain + checkstyleTest) automatically; both already
//                                  run as part of `check`
//   violationSeverity           -> checkstyle.xml's ${org.checkstyle.google.severity} property,
//                                  combined with maxWarnings = 0 below (any violation fails the build)
//   configLocation              -> Checkstyle's default convention already resolves to
//                                  <rootDir>/config/checkstyle/checkstyle.xml
//   propertyExpansion / suppressionsLocation -> configProperties below (see comment there)
//   <dependencies>com.puppycrawl.tools:checkstyle</dependencies> -> toolVersion below
checkstyle {
    toolVersion = libs.findVersion("checkstyle").get().requiredVersion
    // Resolved against <rootDir>/config/checkstyle/checkstyle.xml by default — see that file.
    maxWarnings = 0
    isIgnoreFailures = false
    configDirectory = checkstyleConfigDir
    // Gradle's own ${config_loc} auto-wiring (which configDirectory above is documented to drive)
    // does not reliably fire on this Gradle version — a long-standing upstream bug across several
    // Gradle releases (gradle/gradle#9761, #9762, #11058, #16837), and Gradle hard-rejects setting
    // config_loc here directly. So checkstyle.xml's SuppressionFilter/SuppressionXpathFilter read
    // two of *our own* property names instead (not the reserved config_loc), resolved to real
    // absolute paths here. Required now that both filters are optional="false" in checkstyle.xml —
    // without this, checkstyleMain/checkstyleTest fail outright ("Unable to create Root Module")
    // instead of silently skipping suppressions. Verified with a real suppress rule flipping the
    // build from FAILED to SUCCESSFUL only once these were supplied.
    configProperties = mapOf(
        "org.checkstyle.google.suppressionfilter.config" to
            checkstyleConfigDir.file("checkstyle-suppressions.xml").asFile.absolutePath,
        "org.checkstyle.google.suppressionxpathfilter.config" to
            checkstyleConfigDir.file("checkstyle-xpath-suppressions.xml").asFile.absolutePath,
    )
}

tasks.withType<Checkstyle>().configureEach {
    isShowViolations = true
}

val spotbugsExcludeFile = rootProject.layout.projectDirectory.file("config/spotbugs/spotbugs_exclude.xml")

// Equivalent of the team's former spotbugs-maven-plugin <configuration> block:
//   skip                    -> not needed; this convention plugin is only applied where wanted
//   failOnError             -> N/A in Gradle: a real SpotBugs tool error always fails the build
//   effort=Max              -> effort = Effort.MAX (below)
//   threshold=Low           -> reportLevel = Confidence.LOW (below) — reports bugs down to Low
//                              confidence; combined with ignoreFailures=false, any such bug fails
//                              the build, which is also this Maven config's failThreshold=Low
//   includeTests=true       -> N/A: the Gradle plugin registers a task per source set
//                              (spotbugsMain + spotbugsTest) automatically; both run as part of
//                              `check`
//   xmlOutput=true          -> the xml report is enabled per-task below
//   excludeFilterFile       -> excludeFilter (below), pointing at config/spotbugs/spotbugs_exclude.xml
//   <dependencies>com.github.spotbugs:spotbugs</dependencies> -> toolVersion (below)
configure<SpotBugsExtension> {
    toolVersion = libs.findVersion("spotbugs").get().requiredVersion
    effort = Effort.MAX
    reportLevel = Confidence.LOW
    ignoreFailures = false
    excludeFilter = spotbugsExcludeFile.asFile
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("xml") {
        required = true
    }
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
    dependsOn(tasks.named("checkstyleMain"))
    dependsOn(tasks.named("spotbugsMain"))
    dependsOn(tasks.named("jacocoTestReport"))
}

// The "no domain type outside libs/share" rule from Architecture §8 is enforced in CI starting
// Modernization Plan Phase 2, once libs/share actually has a domain package to police. Not wired
// in here.
