package com.rednavis.metaldesk.api.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.cart.CartTestSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Display currency (BRD FR-1.8): converted by the server, only for display, with rates that say
 * they are fake, and with prices, taxes <em>and</em> totals all recomputed and still adding up.
 */
class DisplayCurrencyTest extends CartTestSupport {

  private static final BigDecimal RATE = new BigDecimal("2.00");
  private static final String PRICE = "unitPrice";
  private static final String TOTALS = "totals";
  private static final String AMOUNT_KEY = "amount";
  private static final String CURRENCY = "currency";
  private static final String USD = "USD";
  private static final String CART = "/api/cart";
  private static final String PRODUCT = "/api/catalog/products/";
  private static final String PRICE_KEY = "price";

  @BeforeEach
  void seedData() {
    seedCatalog();
  }

  private static BigDecimal amount(Object price) {
    return new BigDecimal((String) ((Map<?, ?>) price).get(AMOUNT_KEY));
  }

  private static BigDecimal inUsd(BigDecimal euro) {
    return euro.multiply(RATE).setScale(2, RoundingMode.HALF_UP);
  }

  @Test
  void theOptionsSayTheRatesAreFakeAndNameTheSettlementCurrency() {
    final Called options = call(HttpMethod.GET, "/api/currencies", null, null, null);

    assertEquals(200, options.status());
    assertEquals("EUR", options.body().get("settlement"));
    assertEquals("FAKE", options.body().get("rateSource"));
    final List<?> listed = (List<?>) options.body().get("options");
    assertEquals(
        List.of("EUR", USD), listed.stream().map(o -> ((Map<?, ?>) o).get("code")).toList());
    assertEquals("2.00", ((Map<?, ?>) listed.get(1)).get("perSettlementUnit"));
  }

  @Test
  void productPriceIsConvertedAndCarriesTheDisplayCurrency() {
    final Called euro = call(HttpMethod.GET, PRODUCT + GOLD_1, null, null, null);
    final Called dollar =
        call(HttpMethod.GET, PRODUCT + GOLD_1 + "?currency=usd", null, null, null);

    assertEquals("EUR", ((Map<?, ?>) euro.body().get(PRICE_KEY)).get(CURRENCY));
    assertEquals(USD, ((Map<?, ?>) dollar.body().get(PRICE_KEY)).get(CURRENCY));
    assertEquals(inUsd(amount(euro.body().get(PRICE_KEY))), amount(dollar.body().get(PRICE_KEY)));
  }

  @Test
  void onRequestProductStaysWithoutPriceInAnyCurrency() {
    final Called dollar =
        call(HttpMethod.GET, PRODUCT + ON_REQUEST + "?currency=USD", null, null, null);

    assertEquals("ON_REQUEST", dollar.body().get("pricingMode"));
    assertNull(dollar.body().get(PRICE_KEY), "never a zero price");
  }

  @Test
  void cartPricesTaxesAndTotalsAllRecomputeAndStillAddUp() {
    final Called first = add(null, null, GOLD_1);
    add(first.cookie(), null, TAXED);
    changeQuantity(first.cookie(), GOLD_1, 3);
    final Called euro = call(HttpMethod.GET, CART, first.cookie(), null, null);
    final Called dollar = call(HttpMethod.GET, CART + "?currency=USD", first.cookie(), null, null);

    BigDecimal net = BigDecimal.ZERO;
    BigDecimal tax = BigDecimal.ZERO;
    for (int i = 0; i < euro.lines().size(); i++) {
      final Map<String, Object> inEuro = euro.lines().get(i);
      final Map<String, Object> inDollar = dollar.lines().get(i);
      assertEquals(inUsd(amount(inEuro.get(PRICE))), amount(inDollar.get(PRICE)), "unit price");
      assertEquals(
          inUsd(amount(inEuro.get("lineNet"))), amount(inDollar.get("lineNet")), "line net");
      assertEquals(
          inUsd(amount(inEuro.get("lineTax"))), amount(inDollar.get("lineTax")), "line tax");
      net = net.add(amount(inDollar.get("lineNet")));
      tax = tax.add(amount(inDollar.get("lineTax")));
    }
    final Map<?, ?> totals = (Map<?, ?>) dollar.body().get(TOTALS);
    assertEquals(net, amount(totals.get("net")), "net is the sum of the converted lines");
    assertEquals(tax, amount(totals.get("tax")), "tax is the sum of the converted lines");
    assertEquals(net.add(tax), amount(totals.get("total")), "the displayed total adds up");
    assertEquals(USD, ((Map<?, ?>) totals.get("total")).get(CURRENCY));
    final BigDecimal euroTotal = amount(((Map<?, ?>) euro.body().get(TOTALS)).get("total"));
    assertTrue(
        net.add(tax)
                .divide(RATE, 2, RoundingMode.HALF_UP)
                .subtract(euroTotal)
                .abs()
                .compareTo(new BigDecimal("0.02"))
            <= 0,
        "within rounding of the settlement total");
  }

  @Test
  void anUnknownOrUnofferedCurrencyIsRefused() {
    final Called bogus = call(HttpMethod.GET, PRODUCT + GOLD_1 + "?currency=XXX", null, null, null);

    assertEquals(400, bogus.status());
    assertEquals("currency.unsupported", bogus.body().get("code"));
  }

  @Test
  void theSettlementCurrencyIsTheDefaultAndChangesNothing() {
    final Called first = add(null, null, GOLD_1);
    final Called plain = call(HttpMethod.GET, CART, first.cookie(), null, null);
    final Called explicit =
        call(HttpMethod.GET, CART + "?currency=EUR", first.cookie(), null, null);

    assertEquals(plain.body(), explicit.body());
  }
}
