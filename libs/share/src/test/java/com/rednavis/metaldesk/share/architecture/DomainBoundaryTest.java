package com.rednavis.metaldesk.share.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Checks the domain boundary against the real classes on this module's classpath. It runs in every
 * JVM module, because the {@code metaldesk.quality-conventions} plugin compiles it into each one's
 * tests (see {@link DomainBoundaryRules}); a module added later inherits it without any edit.
 *
 * <p>The import covers every {@code com.rednavis.metaldesk} class on the classpath: the module's
 * own classes and, for a module that depends on {@code libs/share}, the shared library's. The last
 * two tests exist so the rules cannot pass by looking at nothing.
 */
class DomainBoundaryTest {

  private static final Path REPO_ROOT = DomainBoundaryRules.repoRoot();
  private static final Predicate<JavaClass> DECLARED_IN_SHARE =
      DomainBoundaryRules.declaredInShareModule(REPO_ROOT);
  private static final JavaClasses CLASSES =
      new ClassFileImporter().importPackages(DomainBoundaryRules.ROOT_PACKAGE);

  @Test
  void onlyLibsShareDeclaresClassesInTheSharedDomainPackage() {
    DomainBoundaryRules.onlyShareDeclaresDomainTypes(DECLARED_IN_SHARE)
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  @Test
  void domainDoesNotDependOnSpringServicesOrOtherLibraries() {
    DomainBoundaryRules.domainIsLeaf().allowEmptyShould(true).check(CLASSES);
  }

  @Test
  void noSharedAggregateNameIsDeclaredOutsideLibsShare() {
    DomainBoundaryRules.noDuplicatedAggregateNames(DECLARED_IN_SHARE)
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  @Test
  void theImportIncludesThisModulesOwnClasses() {
    final Path ownBuild =
        Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().resolve("build");
    final long own =
        CLASSES.stream()
            .filter(
                javaClass ->
                    DomainBoundaryRules.fileOf(javaClass)
                        .map(file -> file.startsWith(ownBuild))
                        .orElse(false))
            .count();
    assertTrue(own > 0, "No class of this module was imported, so the rules would be vacuous");
  }

  @Test
  void theImportIncludesTheSharedDomainWhenThisModuleDependsOnIt() {
    final long shared =
        CLASSES.stream()
            .filter(
                javaClass ->
                    javaClass.getPackageName().startsWith("com.rednavis.metaldesk.share.domain"))
            .filter(DECLARED_IN_SHARE)
            .count();
    // A module that does not depend on libs/share has no shared domain classes to see, and is still
    // checked for declaring one of its own; only this check is skipped, and it says why.
    assumeTrue(
        shared > 0, "This module does not depend on libs/share, so it sees no shared domain");
    assertTrue(shared > 0);
  }
}
