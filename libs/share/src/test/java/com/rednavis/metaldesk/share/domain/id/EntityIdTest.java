package com.rednavis.metaldesk.share.domain.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EntityIdTest {

  private static final List<Class<? extends EntityId>> ID_TYPES =
      List.of(
          CustomerId.class,
          ProductId.class,
          OrderId.class,
          CategoryId.class,
          FulfillmentTierId.class);

  /* default */ static List<Function<String, EntityId>> factories() {
    return List.of(
        CustomerId::new, ProductId::new, OrderId::new, CategoryId::new, FulfillmentTierId::new);
  }

  @ParameterizedTest
  @MethodSource("factories")
  void everyIdentifierKeepsItsValue(Function<String, EntityId> factory) {
    assertEquals("abc-1", factory.apply("abc-1").value());
  }

  @ParameterizedTest
  @MethodSource("factories")
  void everyIdentifierRejectsNull(Function<String, EntityId> factory) {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> factory.apply(null));
    assertEquals("id.blank", failure.code());
  }

  @ParameterizedTest
  @MethodSource("factories")
  void everyIdentifierRejectsBlank(Function<String, EntityId> factory) {
    for (final String blank : List.of("", " ", "\t\n")) {
      assertThrows(ValidationException.class, () -> factory.apply(blank));
    }
  }

  @Test
  void identifiersWithTheSameValueButDifferentTypesAreNotEqual() {
    assertNotEquals(new CustomerId("1"), new ProductId("1"));
  }

  @Test
  void noTwoIdentifierTypesAreAssignmentCompatible() {
    for (final Class<? extends EntityId> left : ID_TYPES) {
      for (final Class<? extends EntityId> right : ID_TYPES) {
        if (left != right) {
          assertFalse(left.isAssignableFrom(right), left + " must not accept " + right);
        }
      }
    }
  }

  /**
   * Fails to compile, not just to run, if two identifier types stop being independent: a subtype
   * label placed after its supertype is a "dominated label" error, and a sixth permitted type makes
   * the switch non-exhaustive.
   */
  @Test
  void switchOverTheSealedContractIsExhaustiveAndUndominated() {
    final List<EntityId> ids =
        List.of(
            new CustomerId("a"),
            new ProductId("a"),
            new OrderId("a"),
            new CategoryId("a"),
            new FulfillmentTierId("a"));

    final List<String> kinds =
        ids.stream()
            .map(
                id ->
                    switch (id) {
                      case CustomerId _ -> "customer";
                      case ProductId _ -> "product";
                      case OrderId _ -> "order";
                      case CategoryId _ -> "category";
                      case FulfillmentTierId _ -> "tier";
                    })
            .toList();

    assertEquals(List.of("customer", "product", "order", "category", "tier"), kinds);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void requireValueRejectsBlank(String value) {
    assertThrows(ValidationException.class, () -> EntityId.requireValue(value, "X"));
  }
}
