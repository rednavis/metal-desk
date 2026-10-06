package com.rednavis.metaldesk.api.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/** Buy-now, the uniform checkout basket, and the delivery profile (BRD FR-3.2, FR-3.5). */
class CheckoutBasketTest extends CartTestSupport {

  private static final String BUY_NOW = "/api/cart/buy-now";
  private static final String PROFILE = "/api/cart/delivery-profile";

  @Autowired private BasketService baskets;

  @BeforeEach
  void seedData() {
    seedCatalog();
  }

  @Test
  void buyNowOfPricedProductIsOneLineBasketAndLeavesTheCartAlone() {
    final Called existing = add(null, null, GOLD_2);

    final CheckoutBasket basket = baskets.buyNow(GOLD_1).block();

    assertEquals(CheckoutBasket.Source.BUY_NOW, basket.source());
    assertEquals(Optional.empty(), basket.cart());
    assertEquals(1, basket.lines().size());
    assertEquals(new ProductId(GOLD_1), basket.lines().get(0).productId());
    assertEquals(1, basket.lines().get(0).quantity().value());
    assertTrue(basket.checkoutable());
    assertEquals(1, read(existing.cookie(), null).lines().size(), "the cart is untouched");
    assertEquals(1, read(existing.cookie(), null).quantityOf(GOLD_2));
  }

  @Test
  void buyNowOverHttpIsOneLinePreviewWithNoCartAndNoCookie() {
    final Called called = call(HttpMethod.POST, BUY_NOW, null, null, Map.of("productId", GOLD_1));

    assertEquals(200, called.status());
    assertEquals(1, called.lines().size());
    assertEquals(1, called.body().get("itemCount"));
    assertNull(called.body().get("cartId"));
    assertNull(called.cookie());
    assertEquals(200, read(null, null).status());
    assertEquals(true, read(null, null).body().get("empty"));
  }

  @Test
  void buyNowOfAnUnpricedProductIs400() {
    final Called called =
        call(HttpMethod.POST, BUY_NOW, null, null, Map.of("productId", ON_REQUEST));

    assertEquals(400, called.status());
    assertEquals("cart.product-unpriced", called.body().get("code"));
  }

  @Test
  void buyNowOfAnUnknownProductIs404() {
    assertEquals(
        404, call(HttpMethod.POST, BUY_NOW, null, null, Map.of("productId", "nope")).status());
  }

  @Test
  void cartBasketHasOrderLinesAndTotalsByBr5WithNoDelivery() {
    final Called first = add(null, null, GOLD_1);
    add(first.cookie(), null, TAXED);
    changeQuantity(first.cookie(), TAXED, 2);

    final CheckoutBasket basket = baskets.basketFor(first.cookie(), null).block();

    assertEquals(CheckoutBasket.Source.CART, basket.source());
    assertEquals(2, basket.lines().size());
    assertTrue(basket.unpriced().isEmpty());
    assertTrue(basket.checkoutable());
    final OrderTotals totals = basket.totals().orElseThrow();
    assertEquals(totals.net().plus(totals.tax()), totals.grandTotal());
    assertTrue(totals.delivery().isZero());
    assertEquals(
        basket.lines().stream()
            .map(line -> line.lineTax().amount())
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
        totals.tax().amount());
  }

  @Test
  void emptyCartBasketIsNotCheckoutable() {
    final CheckoutBasket basket = baskets.basketFor(null, null).block();

    assertTrue(basket.lines().isEmpty());
    assertFalse(basket.checkoutable());
    assertEquals(Optional.empty(), basket.totals());
  }

  @Test
  void cartWithUnpricedLineIsNotCheckoutable() {
    final Called first = add(null, null, GOLD_1);
    seed.unpricedProduct(
        GOLD_1,
        "Cart gold one",
        seed.category(
            BARS, com.rednavis.metaldesk.share.domain.catalog.TaxCategory.INVESTMENT_GRADE));

    final CheckoutBasket basket = baskets.basketFor(first.cookie(), null).block();

    assertEquals(java.util.List.of(new ProductId(GOLD_1)), basket.unpriced());
    assertFalse(basket.checkoutable());
    seedCatalog();
  }

  @Test
  void theDeliveryProfileIsTheSignedInCustomersSavedDeliveryAddress() {
    final String email = freshEmail();
    registerAndVerify(email);
    final String token = signIn(email, PASSWORD);
    // A registered customer has no saved address yet.
    assertEquals(204, call(HttpMethod.GET, PROFILE, null, token, null).status());
  }

  @Test
  void customerWithSavedDeliveryAddressGetsItToPrefillCheckout() {
    final String email = freshEmail();
    registerAndVerify(email);
    final String id = customerId(email);
    saveDeliveryAddress(id);
    final String token = signIn(email, PASSWORD);

    final Called called = call(HttpMethod.GET, PROFILE, null, token, null);

    assertEquals(200, called.status());
    assertEquals("1 Main Street", called.body().get("street"));
    assertEquals("Berlin", called.body().get("city"));
    assertEquals("DE", called.body().get("country"));
    assertEquals("10115", called.body().get("postalCode"));
  }

  @Test
  void theDeliveryProfileNeedsToken() {
    assertEquals(401, call(HttpMethod.GET, PROFILE, null, null, null).status());
  }
}
