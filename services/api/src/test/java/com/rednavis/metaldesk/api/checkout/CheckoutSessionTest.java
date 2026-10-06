package com.rednavis.metaldesk.api.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpMethod;

/**
 * Starting a checkout, who may touch a session, and the verified-account gate (BRD FR-2.3, FR-3.5).
 */
class CheckoutSessionTest extends CheckoutTestSupport {

  private static final String UNVERIFIED = "checkout.account-unverified";
  private static final String CODE = "code";

  @Autowired private CustomerRepository customers;
  @Autowired private ReactiveMongoTemplate mongo;

  @BeforeEach
  void seedData() {
    seedCatalog();
  }

  private String unverifiedToken(String email) {
    register(email);
    return Objects.requireNonNull(signIn(email, PASSWORD));
  }

  @Test
  void guestStartsCheckoutFromTheCartAndSeesTheBasket() {
    final Called started = startFromCart(null);

    assertEquals(201, started.status());
    assertNotNull(started.body().get("checkoutId"));
    assertEquals("CART", started.body().get("source"));
    final Map<?, ?> basket = (Map<?, ?>) started.body().get("basket");
    assertEquals(1, basket.get("itemCount"));
    assertNull(started.body().get("details"));
  }

  @Test
  void signedInVerifiedCustomerStartsCheckout() {
    final Called started = startFromCart(signedInToken());

    assertEquals(201, started.status());
  }

  @Test
  void emptyCartCannotStartCheckout() {
    final Called started = call(HttpMethod.POST, SESSIONS, null, null, null);

    assertEquals(400, started.status());
    assertEquals("checkout.basket-empty", started.body().get(CODE));
  }

  @Test
  void cartWithAnUnpricedLineCannotStartCheckout() {
    final Called cart = add(null, null, GOLD_2);
    seed.unpricedProduct(
        GOLD_2, "Cart gold two", seed.category(BARS, TaxCategory.INVESTMENT_GRADE));

    final Called started = call(HttpMethod.POST, SESSIONS, cart.cookie(), null, null);

    assertEquals(409, started.status());
    assertEquals("checkout.basket-unpriced", started.body().get(CODE));
    seedCatalog();
  }

  @Test
  void buyNowStartsCheckoutOfOneUnitAndLeavesTheCartAlone() {
    final Called cart = add(null, null, GOLD_2);

    final Called started =
        call(HttpMethod.POST, SESSIONS, cart.cookie(), null, Map.of("buyNowProductId", GOLD_1));

    assertEquals(201, started.status());
    assertEquals("BUY_NOW", started.body().get("source"));
    assertEquals(1, ((Map<?, ?>) started.body().get("basket")).get("itemCount"));
    assertEquals(1, read(cart.cookie(), null).quantityOf(GOLD_2));
    assertEquals(0, read(cart.cookie(), null).quantityOf(GOLD_1));
  }

  @Test
  void buyNowOfAnUnpricedProductIs400() {
    final Called started =
        call(HttpMethod.POST, SESSIONS, null, null, Map.of("buyNowProductId", ON_REQUEST));

    assertEquals(400, started.status());
    assertEquals("cart.product-unpriced", started.body().get(CODE));
  }

  @Test
  void signedInUnverifiedCustomerCannotStartCheckout() {
    final String token = unverifiedToken(freshEmail());

    final Called started = startFromCart(token);

    assertEquals(409, started.status());
    assertEquals(UNVERIFIED, started.body().get(CODE));
  }

  @Test
  void signedInUnverifiedCustomerIsRejectedAtStepOneWithAnActionableCode() {
    final String guestCheckout = checkoutId(startFromCart(null));
    final String token = unverifiedToken(freshEmail());

    final Called called = submit(guestCheckout, token, validForm(freshEmail()));

    assertEquals(409, called.status());
    assertEquals(UNVERIFIED, called.body().get(CODE));
    assertNull(session(guestCheckout, null).body().get("details"), "and nothing was recorded");
  }

  @Test
  void tokenThatSaysUnverifiedButWhoseAccountHasSinceBeenVerifiedIsLetThrough() {
    final String email = freshEmail();
    final Object reference = register(email).body().get("reference");
    final String staleToken = Objects.requireNonNull(signIn(email, PASSWORD));
    call(
        HttpMethod.POST,
        "/api/account/verify-email",
        null,
        null,
        Map.of(
            "reference",
            reference,
            CODE,
            com.rednavis.metaldesk.api.account.MailInspector.typedCode(mailTo(email).get(0))));

    assertEquals(201, startFromCart(staleToken).status());
  }

  @Test
  void sessionStartedByCustomerBelongsToThemAlone() {
    final String owner = signedInToken();
    final String checkout = checkoutId(startFromCart(owner));
    final String other = signedInToken();

    assertEquals(200, session(checkout, owner).status());
    assertEquals(404, session(checkout, other).status());
    assertEquals(404, session(checkout, null).status());
    assertEquals(404, submit(checkout, other, validForm(freshEmail())).status());
  }

  @Test
  void guestSessionIsReachableByItsId() {
    final String checkout = checkoutId(startFromCart(null));

    assertEquals(200, session(checkout, null).status());
  }

  @Test
  void expiredSessionIsGone() {
    final String checkout = checkoutId(startFromCart(null));
    mongo
        .updateFirst(
            Query.query(Criteria.where("_id").is(checkout)),
            new Update().set("expiresAt", Instant.now().minusSeconds(60)),
            "checkout_sessions")
        .block();

    assertEquals(404, session(checkout, null).status());
    assertEquals(404, submit(checkout, null, validForm(freshEmail())).status());
  }

  @Test
  void theSavedDeliveryAddressPrefillsButNeverOverridesTheSubmittedValues() {
    final String email = freshEmail();
    registerAndVerify(email);
    saveDeliveryAddress(Objects.requireNonNull(customers.findByEmail(email).block()).id());
    final String token = Objects.requireNonNull(signIn(email, PASSWORD));
    final Called profile = call(HttpMethod.GET, "/api/cart/delivery-profile", null, token, null);
    assertEquals("1 Main Street", profile.body().get("street"));
    final String checkout = checkoutId(startFromCart(token));

    final Called called =
        submit(checkout, token, with(validForm(email), "street", "99 Edited Road"));

    assertEquals(200, called.status());
    assertEquals("99 Edited Road", ((Map<?, ?>) called.body().get("details")).get("street"));
  }
}
