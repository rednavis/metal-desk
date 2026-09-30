package com.rednavis.metaldesk.api.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.persistence.document.CartDocument;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

/** The cart endpoints over HTTP against real MongoDB (BRD FR-3.1 to FR-3.4). */
class CartApiTest extends CartTestSupport {

  private static final String PRICE = "unitPrice";
  private static final String ITEM_COUNT = "itemCount";
  private static final String CODE = "code";
  private static final String TOTALS = "totals";

  @Autowired private ReactiveMongoTemplate mongo;

  @BeforeEach
  void seedData() {
    seedCatalog();
  }

  @Test
  void readingWithNoCartIsTheEmptyStateNotA404AndCreatesNothing() {
    final Called called = read(null, null);

    assertEquals(200, called.status());
    assertEquals(true, called.body().get("empty"));
    assertEquals(0, called.body().get(ITEM_COUNT));
    assertEquals(0, called.lines().size());
    assertNull(called.body().get("cartId"));
    assertNull(called.body().get(TOTALS));
    assertNull(called.cookie(), "a read must not create a cart");
  }

  @Test
  void addingSetsTheCartCookieAndShowsTheLine() {
    final Called called = add(null, null, GOLD_1);

    assertEquals(200, called.status());
    assertNotNull(called.cookie());
    assertEquals(called.cookie(), called.body().get("cartId"));
    assertEquals(1, called.quantityOf(GOLD_1));
    assertEquals(1, called.body().get(ITEM_COUNT));
    assertEquals(false, called.body().get("empty"));
  }

  @Test
  void addingTheSameProductTwiceLeavesOneLineAndDoesNotIncrement() {
    final Called first = add(null, null, GOLD_1);
    changeQuantity(first.cookie(), GOLD_1, 4);

    final Called again = add(first.cookie(), null, GOLD_1);

    assertEquals(1, again.lines().size());
    assertEquals(4, again.quantityOf(GOLD_1), "add is a no-op when the line exists");
  }

  @Test
  void quantityAboveTheCapIsRefusedAndTheCartIsUnchanged() {
    final Called first = add(null, null, GOLD_1);

    final Called refused = changeQuantity(first.cookie(), GOLD_1, Quantity.MAX + 1);

    assertEquals(400, refused.status());
    assertEquals("quantity.above-cap", refused.body().get(CODE));
    assertEquals(1, read(first.cookie(), null).quantityOf(GOLD_1));
  }

  @Test
  void zeroQuantityIsRefused() {
    final Called first = add(null, null, GOLD_1);

    final Called refused = changeQuantity(first.cookie(), GOLD_1, 0);

    assertEquals(400, refused.status());
    assertEquals("quantity.not-positive", refused.body().get(CODE));
  }

  @Test
  void quantityUpToTheCapIsAccepted() {
    final Called first = add(null, null, GOLD_1);

    assertEquals(
        Quantity.MAX, changeQuantity(first.cookie(), GOLD_1, Quantity.MAX).quantityOf(GOLD_1));
  }

  @Test
  void changingQuantityOfLineThatIsNotThereIs404() {
    final Called first = add(null, null, GOLD_1);

    final Called missing = changeQuantity(first.cookie(), GOLD_2, 2);

    assertEquals(404, missing.status());
    assertEquals("cart.line-not-found", missing.body().get(CODE));
    assertEquals(404, changeQuantity(null, GOLD_2, 2).status());
  }

  @Test
  void removingLineRemovesItAndRemovingAbsentOneIsNoOp() {
    final Called first = add(null, null, GOLD_1);
    add(first.cookie(), null, GOLD_2);

    final Called removed =
        call(HttpMethod.DELETE, "/api/cart/lines/" + GOLD_1, first.cookie(), null, null);
    assertEquals(0, removed.quantityOf(GOLD_1));
    assertEquals(1, removed.quantityOf(GOLD_2));

    final Called again =
        call(HttpMethod.DELETE, "/api/cart/lines/" + GOLD_1, first.cookie(), null, null);
    assertEquals(200, again.status());
    assertEquals(1, again.lines().size());
    assertEquals(
        200, call(HttpMethod.DELETE, "/api/cart/lines/" + GOLD_1, null, null, null).status());
  }

  @Test
  void clearingLeavesAnExplicitEmptyState() {
    final Called first = add(null, null, GOLD_1);

    final Called cleared = call(HttpMethod.DELETE, "/api/cart", first.cookie(), null, null);

    assertEquals(200, cleared.status());
    assertEquals(true, cleared.body().get("empty"));
    assertEquals(0, cleared.lines().size());
    assertEquals(0, cleared.body().get(ITEM_COUNT));
  }

  @Test
  void productSoldOnRequestCannotBeAdded() {
    final Called refused = add(null, null, ON_REQUEST);

    assertEquals(400, refused.status());
    assertEquals("cart.product-unpriced", refused.body().get(CODE));
  }

  @Test
  void unknownProductIs404AndMalformedIdIs400() {
    assertEquals(404, add(null, null, "no-such-product").status());
    assertEquals(400, add(null, null, "bad.id").status());
  }

  @Test
  void lineShowsIdentityQuantityCapPriceAndTax() {
    final Called first = add(null, null, TAXED);
    final Called called = changeQuantity(first.cookie(), TAXED, 2);

    final Map<String, Object> line = called.lines().get(0);
    assertEquals("Cart taxed", line.get("name"));
    assertEquals(Quantity.MAX, line.get("maxQuantity"));
    assertEquals("FIXED", line.get("pricingMode"));
    assertEquals("19", line.get("taxRatePercent"));
    final BigDecimal unit = amount(line.get(PRICE));
    assertEquals(unit.multiply(BigDecimal.valueOf(2)).setScale(2), amount(line.get("lineNet")));
    assertEquals(
        amount(line.get("lineNet"))
            .multiply(new BigDecimal("0.19"))
            .setScale(2, java.math.RoundingMode.HALF_UP),
        amount(line.get("lineTax")));
  }

  @Test
  void theRunningTotalIsNetPlusTaxOverTheLinesWithNoDelivery() {
    final Called first = add(null, null, GOLD_1);
    add(first.cookie(), null, TAXED);
    final Called called = changeQuantity(first.cookie(), TAXED, 3);

    final BigDecimal net =
        called.lines().stream()
            .map(line -> amount(line.get("lineNet")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    final BigDecimal tax =
        called.lines().stream()
            .map(line -> amount(line.get("lineTax")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    final Map<?, ?> totals = (Map<?, ?>) called.body().get(TOTALS);
    assertEquals(net, amount(totals.get("net")));
    assertEquals(tax, amount(totals.get("tax")));
    assertEquals(net.add(tax), amount(totals.get("total")));
    assertEquals(true, called.body().get("complete"));
    assertEquals(4, called.body().get(ITEM_COUNT));
  }

  @Test
  void theUnitPriceFollowsTheReferencePriceBetweenTwoReads() {
    final Called first = add(null, null, GOLD_1);
    seed.observeGold("60.00");
    final BigDecimal before = amount(read(first.cookie(), null).lines().get(0).get(PRICE));

    seed.observeGold("90.00");
    final BigDecimal after = amount(read(first.cookie(), null).lines().get(0).get(PRICE));

    assertTrue(after.compareTo(before) > 0, before + " then " + after);
    assertEquals(new BigDecimal("1.5"), after.divide(before, 1, java.math.RoundingMode.HALF_UP));
  }

  @Test
  void theStoredCartHoldsNoPrice() {
    final Called first = add(null, null, GOLD_1);
    changeQuantity(first.cookie(), GOLD_1, 3);

    final CartDocument stored =
        Objects.requireNonNull(mongo.findById(first.cookie(), CartDocument.class).block());
    final String json =
        Objects.requireNonNull(
                mongo
                    .getCollection("carts")
                    .flatMap(
                        c ->
                            Mono.from(c.find(new Document("_id", first.cookie())).first())
                                .map(Document::toJson))
                    .block())
            .toLowerCase(Locale.ROOT);

    assertEquals(3, stored.lines().get(0).quantity());
    assertEquals(false, json.contains("price"), json);
    assertEquals(false, json.contains("amount"), json);
  }

  @Test
  void cartWithLineThatHasNoPriceIsIncompleteButStillShowsIt() {
    final Called first = add(null, null, GOLD_1);
    seed.unpricedProduct(
        GOLD_1,
        "Cart gold one",
        seed.category(
            BARS, com.rednavis.metaldesk.share.domain.catalog.TaxCategory.INVESTMENT_GRADE));

    final Called called = read(first.cookie(), null);

    assertEquals(false, called.body().get("complete"));
    assertEquals("ON_REQUEST", called.lines().get(0).get("pricingMode"));
    assertNull(called.lines().get(0).get(PRICE));
    assertNull(called.body().get(TOTALS));
    seedCatalog();
  }

  @Test
  void theCookieIsHttpOnlyAndSameSiteAndScopedToTheCart() {
    final String header =
        client
            .post()
            .uri("/api/cart/lines")
            .bodyValue(Map.of("productId", GOLD_1))
            .exchange()
            .expectBody()
            .returnResult()
            .getResponseHeaders()
            .getFirst("Set-Cookie");

    assertNotNull(header);
    assertTrue(header.contains("HttpOnly"), header);
    assertTrue(header.contains("SameSite=Lax"), header);
    assertTrue(header.contains("Path=/api/cart"), header);
    assertTrue(header.contains("Max-Age="), header);
  }

  private static BigDecimal amount(Object price) {
    return new BigDecimal((String) ((Map<?, ?>) price).get("amount"));
  }
}
