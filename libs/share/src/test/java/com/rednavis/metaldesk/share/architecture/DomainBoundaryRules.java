package com.rednavis.metaldesk.share.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.Source;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The architecture rules that keep the domain model in one place (Architecture section 8, Lessons
 * Learned "The shared library nobody depends on"). They are written here once, in {@code
 * libs/share}, and the {@code metaldesk.quality-conventions} plugin compiles this class and {@link
 * DomainBoundaryTest} into every other JVM module's tests, so each module checks the classes on its
 * own classpath.
 *
 * <p><strong>Why every module runs them.</strong> {@code libs/share} cannot see the classes of
 * {@code services/api} or any module that depends on it, so a check run only here could never see a
 * violation: it would pass forever and look identical to a working rule. Running the rules in each
 * module makes the check non-vacuous, and applying them from the convention plugin means a module
 * added later inherits them without editing its own build file.
 *
 * <p><strong>Where a class is "declared".</strong> A class is in {@code libs/share} if its class
 * file was loaded from under {@code libs/share} in this repository, either its compiled classes or
 * its jar. That is judged from the location, not the package name, because the package name is
 * exactly what a violator would copy.
 */
public final class DomainBoundaryRules {

  /** The package every class of this system lives under. */
  public static final String ROOT_PACKAGE = "com.rednavis.metaldesk";

  /** The shared domain package that only {@code libs/share} may declare classes in. */
  public static final String DOMAIN_PACKAGE = "com.rednavis.metaldesk.share.domain..";

  /** The aggregates whose names must not appear on a second class outside {@code libs/share}. */
  public static final Set<String> AGGREGATE_NAMES =
      Set.of(
          "Order",
          "Customer",
          "Product",
          "OrderLine",
          "PaymentRecord",
          "FulfillmentTier",
          "DeliveryQuote");

  private static final String[] LEAF_PACKAGES = {
    "com.rednavis.metaldesk.share.domain..", "com.rednavis.metaldesk.share.error.."
  };

  private static final String[] FORBIDDEN_DEPS = {
    "org.springframework..",
    "com.rednavis.metaldesk.payments..",
    "com.rednavis.metaldesk.mail..",
    "com.rednavis.metaldesk.api..",
    "com.rednavis.metaldesk.pricingbridge..",
    "com.rednavis.metaldesk.admin.."
  };

  private static final String JAR_SCHEME = "jar";

  private DomainBoundaryRules() {}

  /**
   * Rule 1, Architecture section 8 literally: no class outside {@code libs/share} may be declared
   * in the shared domain package.
   *
   * @param declaredInShare tells whether a class was declared in {@code libs/share}
   * @return the rule
   */
  public static ArchRule onlyShareDeclaresDomainTypes(Predicate<JavaClass> declaredInShare) {
    return ArchRuleDefinition.classes()
        .that()
        .resideInAPackage(DOMAIN_PACKAGE)
        .should(beDeclaredInShare(declaredInShare))
        .because("the domain model lives in libs/share and is never copied into a consumer");
  }

  /**
   * Rule 2: the domain is the leaf of the dependency graph, so it must not depend on Spring, on a
   * service or app module, or on {@code libs/payments} or {@code libs/mail}.
   *
   * @return the rule
   */
  public static ArchRule domainIsLeaf() {
    return ArchRuleDefinition.noClasses()
        .that()
        .resideInAnyPackage(LEAF_PACKAGES)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(FORBIDDEN_DEPS)
        .because(
            "the domain model is the leaf of the build graph, and depends on nothing above it");
  }

  /**
   * Rule 3: no class named like one of the shared aggregates may exist outside {@code libs/share}.
   * It is the mechanical form of "zero duplicated domain types", and the exact shape the failure in
   * the Lessons Learned took.
   *
   * @param declaredInShare tells whether a class was declared in {@code libs/share}
   * @return the rule
   */
  public static ArchRule noDuplicatedAggregateNames(Predicate<JavaClass> declaredInShare) {
    return ArchRuleDefinition.classes()
        .that(
            DescribedPredicate.describe(
                "are named like a shared aggregate",
                (JavaClass javaClass) -> AGGREGATE_NAMES.contains(javaClass.getSimpleName())))
        .should(beDeclaredInShare(declaredInShare))
        .because("a second Order, Customer or Product is the copy-paste this rule exists to stop");
  }

  /**
   * Builds the test for "declared in {@code libs/share}".
   *
   * @param repoRoot the root of this repository
   * @return {@code true} for a class whose file was loaded from under {@code libs/share}
   */
  public static Predicate<JavaClass> declaredInShareModule(Path repoRoot) {
    final Path share = repoRoot.resolve("libs").resolve("share").toAbsolutePath().normalize();
    return javaClass -> fileOf(javaClass).map(file -> file.startsWith(share)).orElse(false);
  }

  /**
   * Finds where a class was loaded from.
   *
   * @param javaClass the class
   * @return the file or jar it came from, or empty if it did not come from a file
   */
  public static Optional<Path> fileOf(JavaClass javaClass) {
    return javaClass
        .getSource()
        .map(Source::getUri)
        .flatMap(DomainBoundaryRules::toFile)
        .map(path -> path.toAbsolutePath().normalize());
  }

  /**
   * Finds this repository's root, from the working directory of the running test.
   *
   * @return the directory that holds {@code settings.gradle.kts}
   * @throws IllegalStateException if no such directory is found above the working directory
   */
  public static Path repoRoot() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (directory != null && !Files.exists(directory.resolve("settings.gradle.kts"))) {
      directory = directory.getParent();
    }
    if (directory == null) {
      throw new IllegalStateException("No settings.gradle.kts above the working directory");
    }
    return directory;
  }

  private static ArchCondition<JavaClass> beDeclaredInShare(Predicate<JavaClass> declaredInShare) {
    return new ArchCondition<>("be declared in libs/share") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (!declaredInShare.test(javaClass)) {
          final String where = fileOf(javaClass).map(Path::toString).orElse("an unknown location");
          events.add(
              SimpleConditionEvent.violated(
                  javaClass, javaClass.getName() + " is declared outside libs/share, in " + where));
        }
      }
    };
  }

  private static Optional<Path> toFile(URI uri) {
    URI file = uri;
    if (JAR_SCHEME.equals(uri.getScheme())) {
      final String spec = uri.getRawSchemeSpecificPart();
      file = URI.create(spec.substring(0, spec.indexOf("!/")));
    }
    return "file".equals(file.getScheme()) ? Optional.of(Path.of(file)) : Optional.empty();
  }
}
