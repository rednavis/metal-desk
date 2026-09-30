package com.rednavis.metaldesk.api.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * A cart's identity survives signing in and out (BRD FR-2.7): the anonymous cart is still there
 * after sign-in and after sign-out, and merging it into a customer's cart happens once.
 */
class CartIdentityTest extends CartTestSupport {

  private static final String CART_ID = "cartId";

  @BeforeEach
  void seedData() {
    seedCatalog();
  }

  @Test
  void anonymousCartIsReadableAfterSignInAndAfterSignOut() {
    final Called anonymous = add(null, null, GOLD_1);
    changeQuantity(anonymous.cookie(), GOLD_1, 4);
    final String token = signedInToken();

    final Called signedIn = read(anonymous.cookie(), token);
    assertEquals(4, signedIn.quantityOf(GOLD_1));
    assertNotNull(signedIn.cookie(), "the cookie must keep pointing at a cart that exists");

    // Signing out is just dropping the token; the cookie is all that is left.
    final Called signedOut = read(signedIn.cookie(), null);
    assertEquals(4, signedOut.quantityOf(GOLD_1));
    assertEquals(signedIn.body().get(CART_ID), signedOut.body().get(CART_ID));
  }

  @Test
  void signedInCustomerFindsTheirCartOnAnotherDeviceWithoutCookie() {
    final String token = signedInToken();
    add(null, token, GOLD_1);

    final Called elsewhere = read(null, token);

    assertEquals(1, elsewhere.quantityOf(GOLD_1));
    assertNotNull(elsewhere.cookie());
  }

  @Test
  void anonymousCartIsMergedIntoTheCustomersCartByProductWithinTheCap() {
    final String token = signedInToken();
    final Called own = add(null, token, GOLD_1);
    changeQuantity(own.cookie(), GOLD_1, 8);
    final Called anonymous = add(null, null, GOLD_1);
    changeQuantity(anonymous.cookie(), GOLD_1, 6);
    add(anonymous.cookie(), null, GOLD_2);

    final Called merged = read(anonymous.cookie(), token);

    assertEquals(10, merged.quantityOf(GOLD_1), "8 + 6, capped at 10");
    assertEquals(1, merged.quantityOf(GOLD_2));
    assertEquals(2, merged.lines().size());
    assertEquals(own.body().get(CART_ID), merged.cookie());
  }

  @Test
  void mergingTwiceDoesNotDoubleTheQuantities() {
    final String token = signedInToken();
    final Called own = add(null, token, GOLD_1);
    changeQuantity(own.cookie(), GOLD_1, 3);
    final Called anonymous = add(null, null, GOLD_1);
    changeQuantity(anonymous.cookie(), GOLD_1, 2);

    final Called first = read(anonymous.cookie(), token);
    final Called second = read(anonymous.cookie(), token);
    final Called third = read(first.cookie(), token);

    assertEquals(5, first.quantityOf(GOLD_1));
    assertEquals(5, second.quantityOf(GOLD_1), "a repeated sign-in with the stale cookie");
    assertEquals(5, third.quantityOf(GOLD_1));
  }

  @Test
  void simultaneousRequestsMergeTheAnonymousCartOnlyOnce() {
    final String token = signedInToken();
    final Called own = add(null, token, GOLD_1);
    changeQuantity(own.cookie(), GOLD_1, 3);
    final Called anonymous = add(null, null, GOLD_1);
    changeQuantity(anonymous.cookie(), GOLD_1, 2);

    Flux.range(0, 6)
        .flatMap(
            i ->
                reactor.core.publisher.Mono.fromCallable(() -> read(anonymous.cookie(), token))
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()))
        .collectList()
        .block();

    assertEquals(5, read(null, token).quantityOf(GOLD_1));
  }

  @Test
  void anonymousCartWithNoCustomerCartIsAdoptedNotCopied() {
    final Called anonymous = add(null, null, GOLD_1);
    final String token = signedInToken();

    final Called adopted = read(anonymous.cookie(), token);

    assertEquals(anonymous.cookie(), adopted.body().get(CART_ID));
    assertEquals(1, adopted.quantityOf(GOLD_1));
  }

  @Test
  void cartOwnedBySomeoneElseIsNeverMergedOrShownToSignedInCustomer() {
    final String owner = signedInToken();
    final Called ownersCart = add(null, owner, GOLD_1);
    final String other = signedInToken();
    add(null, other, GOLD_2);

    final Called seenByOther = read(ownersCart.cookie(), other);

    assertEquals(0, seenByOther.quantityOf(GOLD_1), "someone else's cart must not be merged in");
    assertEquals(1, seenByOther.quantityOf(GOLD_2));
    assertEquals(1, read(null, owner).quantityOf(GOLD_1), "and the owner's cart is untouched");
  }

  @Test
  void twoAnonymousShoppersDoNotShareCart() {
    final Called first = add(null, null, GOLD_1);
    final Called second = add(null, null, GOLD_2);

    assertEquals(
        List.of(1, 0),
        List.of(
            read(first.cookie(), null).quantityOf(GOLD_1),
            read(first.cookie(), null).quantityOf(GOLD_2)));
    assertEquals(1, read(second.cookie(), null).quantityOf(GOLD_2));
  }

  @Test
  void bogusCartReferenceStartsEmptyCart() {
    final Called called = read("not-a-real-reference", null);

    assertEquals(200, called.status());
    assertEquals(true, called.body().get("empty"));
  }
}
