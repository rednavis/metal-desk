package com.rednavis.metaldesk.share.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The negative proof: a rule nobody has seen fail is not known to work. Each test compiles a
 * deliberate violation into a temporary directory, which is outside {@code libs/share}, and asserts
 * that the rule rejects it and names the offending class. A compliant version of the same code is
 * accepted, so the rules are shown to fail on a violation and pass once it is removed.
 *
 * <p>The violations are compiled here, at test time, rather than kept as source files, because a
 * checked-in violating class would break every module's build, and would itself be a class declared
 * in the shared domain package.
 */
class DomainBoundaryViolationTest {

  private static final Predicate<JavaClass> DECLARED_IN_SHARE =
      DomainBoundaryRules.declaredInShareModule(DomainBoundaryRules.repoRoot());

  private static JavaClasses compile(Path directory, Map<String, String> sources)
      throws IOException {
    final Path src = Files.createDirectories(directory.resolve("src"));
    final Path out = Files.createDirectories(directory.resolve("out"));
    final List<String> arguments = new java.util.ArrayList<>(List.of("-d", out.toString()));
    for (final Map.Entry<String, String> source : sources.entrySet()) {
      final Path file = src.resolve(source.getKey().replace('.', '/') + ".java");
      Files.createDirectories(Objects.requireNonNull(file.getParent()));
      Files.writeString(file, source.getValue());
      arguments.add(file.toString());
    }
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
    return new ClassFileImporter().importPath(out);
  }

  private static String declaring(String pkg, String name) {
    return "package " + pkg + "; public class " + name + " {}";
  }

  @Test
  void classDeclaredInTheSharedDomainPackageOutsideLibsShareIsRejected(@TempDir Path directory)
      throws IOException {
    final JavaClasses rogue =
        compile(
            directory,
            Map.of(
                "com.rednavis.metaldesk.share.domain.Rogue",
                declaring("com.rednavis.metaldesk.share.domain", "Rogue")));
    final AssertionError failure =
        assertThrows(
            AssertionError.class,
            () -> DomainBoundaryRules.onlyShareDeclaresDomainTypes(DECLARED_IN_SHARE).check(rogue));
    assertTrue(failure.getMessage().contains("Rogue"), failure.getMessage());
    assertTrue(failure.getMessage().contains("outside libs/share"), failure.getMessage());
  }

  @Test
  void theSameModuleWithoutTheViolationIsAccepted(@TempDir Path directory) throws IOException {
    final JavaClasses clean =
        compile(
            directory,
            Map.of(
                "com.rednavis.metaldesk.api.Fine",
                declaring("com.rednavis.metaldesk.api", "Fine")));
    DomainBoundaryRules.onlyShareDeclaresDomainTypes(DECLARED_IN_SHARE)
        .allowEmptyShould(true)
        .check(clean);
    DomainBoundaryRules.noDuplicatedAggregateNames(DECLARED_IN_SHARE)
        .allowEmptyShould(true)
        .check(clean);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Order",
        "Customer",
        "Product",
        "OrderLine",
        "PaymentRecord",
        "FulfillmentTier",
        "DeliveryQuote"
      })
  void secondClassNamedLikeSharedAggregateIsRejected(String name, @TempDir Path directory)
      throws IOException {
    final JavaClasses copy =
        compile(
            directory,
            Map.of(
                "com.rednavis.metaldesk.api.model." + name,
                declaring("com.rednavis.metaldesk.api.model", name)));
    final AssertionError failure =
        assertThrows(
            AssertionError.class,
            () -> DomainBoundaryRules.noDuplicatedAggregateNames(DECLARED_IN_SHARE).check(copy));
    assertTrue(failure.getMessage().contains("api.model." + name), failure.getMessage());
  }

  @Test
  void springDependencyInTheDomainIsRejected(@TempDir Path directory) throws IOException {
    final JavaClasses leaky =
        compile(
            directory,
            Map.of(
                "org.springframework.stereotype.Component",
                "package org.springframework.stereotype; public @interface Component {}",
                "com.rednavis.metaldesk.share.domain.customer.Leaky",
                "package com.rednavis.metaldesk.share.domain.customer;"
                    + " @org.springframework.stereotype.Component public class Leaky {}"));
    final AssertionError failure =
        assertThrows(AssertionError.class, () -> DomainBoundaryRules.domainIsLeaf().check(leaky));
    assertTrue(failure.getMessage().contains("Leaky"), failure.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"payments", "mail", "api", "pricingbridge", "admin"})
  void dependencyOnAnotherModuleIsRejected(String module, @TempDir Path directory)
      throws IOException {
    final JavaClasses inverted =
        compile(
            directory,
            Map.of(
                "com.rednavis.metaldesk." + module + ".Other",
                declaring("com.rednavis.metaldesk." + module, "Other"),
                "com.rednavis.metaldesk.share.domain.order.Inverted",
                "package com.rednavis.metaldesk.share.domain.order; public class Inverted {"
                    + " com.rednavis.metaldesk."
                    + module
                    + ".Other other; }"));
    final AssertionError failure =
        assertThrows(
            AssertionError.class, () -> DomainBoundaryRules.domainIsLeaf().check(inverted));
    assertTrue(failure.getMessage().contains("Inverted"), failure.getMessage());
  }

  @Test
  void domainClassThatDependsOnNothingAboveItIsAccepted(@TempDir Path directory)
      throws IOException {
    final JavaClasses fine =
        compile(
            directory,
            Map.of(
                "com.rednavis.metaldesk.share.domain.money.Amount",
                "package com.rednavis.metaldesk.share.domain.money; public class Amount {"
                    + " java.math.BigDecimal value; }"));
    DomainBoundaryRules.domainIsLeaf().check(fine);
  }
}
