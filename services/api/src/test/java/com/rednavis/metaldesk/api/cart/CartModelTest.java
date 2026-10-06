package com.rednavis.metaldesk.api.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The cart's own rules, without a database: idempotent add, refused quantities, capped merge. */
class CartModelTest {

  private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
  private static final ProductId GOLD = new ProductId("gold");
  private static final ProductId SILVER = new ProductId("silver");

  private static Cart cart(CartLine... lines) {
    return new Cart(new CartId("c-1"), Optional.empty(), List.of(lines), 0, NOW, NOW);
  }

  private static CartLine line(ProductId product, int quantity) {
    return new CartLine(product, Quantity.of(quantity));
  }

  @Test
  void addingProductTwiceLeavesOneLineOfOneUnit() {
    final Cart once = cart().addIfAbsent(GOLD);
    final Cart twice = once.addIfAbsent(GOLD);

    assertEquals(List.of(line(GOLD, 1)), twice.lines());
    assertSame(once, twice, "a repeated add must be a no-op, not a new cart");
  }

  @Test
  void addingDoesNotIncrementAnExistingQuantity() {
    assertEquals(4, cart(line(GOLD, 4)).addIfAbsent(GOLD).lines().get(0).quantity().value());
  }

  @Test
  void quantityCanBeChangedOnAnExistingLine() {
    assertEquals(
        List.of(line(GOLD, 7), line(SILVER, 1)),
        cart(line(GOLD, 2), line(SILVER, 1)).withQuantity(GOLD, Quantity.of(7)).lines());
  }

  @Test
  void quantityOfAnAbsentLineIsNotFound() {
    final Cart empty = cart();
    final Quantity three = Quantity.of(3);

    final NotFoundException failure =
        assertThrows(
            NotFoundException.class, () -> Objects.requireNonNull(empty.withQuantity(GOLD, three)));

    assertEquals("cart.line-not-found", failure.code());
  }

  @Test
  void quantityAboveTheSharedCapIsRefusedNotClamped() {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> Quantity.of(Quantity.MAX + 1));

    assertEquals("quantity.above-cap", failure.code());
  }

  @Test
  void removingLineRemovesItAndRemovingAbsentOneChangesNothing() {
    final Cart both = cart(line(GOLD, 2), line(SILVER, 1));

    assertEquals(List.of(line(SILVER, 1)), both.without(GOLD).lines());
    assertSame(both, both.without(new ProductId("nothing")));
  }

  @Test
  void clearingEmptiesTheCart() {
    assertEquals(List.of(), cart(line(GOLD, 2)).cleared().lines());
    final Cart empty = cart();
    assertSame(empty, empty.cleared());
  }

  @Test
  void mergeUnionsByProductAndSumsWithinTheCap() {
    final Cart merged =
        cart(line(GOLD, 3), line(SILVER, 1))
            .mergedWith(cart(line(GOLD, 4), line(new ProductId("tin"), 2)));

    assertEquals(
        List.of(line(GOLD, 7), line(SILVER, 1), line(new ProductId("tin"), 2)), merged.lines());
  }

  @Test
  void mergeIsCappedAtTheSharedMaximum() {
    final Cart merged = cart(line(GOLD, 8)).mergedWith(cart(line(GOLD, 9)));

    assertEquals(Quantity.MAX, merged.lines().get(0).quantity().value());
  }

  @Test
  void changeReturnsNewCartAndLeavesTheOldOneAlone() {
    final Cart original = cart();

    final Cart changed = original.addIfAbsent(GOLD);

    assertNotSame(original, changed);
    assertEquals(List.of(), original.lines());
  }

  @Test
  void twoLinesForOneProductAreRefused() {
    final List<CartLine> lines = List.of(line(GOLD, 1), line(GOLD, 2));
    final CartId id = new CartId("c-1");
    final Optional<com.rednavis.metaldesk.share.domain.id.CustomerId> owner = Optional.empty();

    assertThrows(ValidationException.class, () -> new Cart(id, owner, lines, 0, NOW, NOW));
  }

  @Test
  void cartLineHoldsNoPrice() {
    assertEquals(
        List.of("productId", "quantity"),
        Arrays.stream(CartLine.class.getRecordComponents()).map(RecordComponent::getName).toList());
  }
}
