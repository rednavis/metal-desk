package com.rednavis.metaldesk.api.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * The self-service path of BRD FR-5.2, end to end and through the HTTP surface only: catalog, cart,
 * checkout, payment, confirmation, history. Every step is an endpoint call; the only thing read
 * from outside the application is the database (to find out what was stored) and the provider
 * stub's request journal (to find out what was charged).
 */
class CheckoutSelfServiceE2eTest extends E2eTestSupport {

  private static final Pattern BR6_NUMBER = Pattern.compile("\\d{12}");
  private static final String CAPTURED_STUB =
      "{\"status\":\"captured\",\"reference\":\"gw_e2e_1\"}";
  private static final String DECLINED_STUB =
      "{\"status\":\"declined\",\"declineCode\":\"insufficient_funds\"}";
  private static final String TIER_PRICE = "12.50";
  private static final String RESULT = "result";
  private static final String CARD = "CARD";
  private static final String TOTALS = "totals";

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  /** Brings a signed-in customer to a selected method within a tier that covers the order. */
  private String readyWithin(Shopper shopper, String region) {
    configureTier(region, "1000000.00", "100000", TIER_PRICE);
    final String id = startCheckout(shopper, SMALL);
    assertTrue(step1(id, shopper, region).body().containsKey("step1Complete"));
    final Called evaluated = evaluateAs(id, shopper);
    assertEquals("PAYMENT_ALLOWED", evaluated.body().get("stage"));
    assertEquals(200, selectAs(id, shopper, CARD).status());
    return id;
  }

  @Test
  void customerWalksFromCatalogToPaidOrderAndSeesItInHistory() {
    final Shopper shopper = shopper();
    stubAuthorise(GATEWAY, CAPTURED_STUB);

    // 1. the catalog: the product is offered at a price
    final Called product = call(HttpMethod.GET, "/api/catalog/products/" + SMALL, null, null, null);
    assertEquals(200, product.status());
    assertNotNull(product.body().get("price"), "a priced product shows its price");

    // 2. the cart: adding it twice is idempotent (FR-3.1)
    add(null, shopper.token(), SMALL);
    final Called again = add(null, shopper.token(), SMALL);
    assertEquals(1, again.quantityOf(SMALL), "a repeat add does not duplicate the line");

    // 3-4. step 1 inside the tier ceilings, then delivery is evaluated at the configured price
    final String region = nextRegion();
    configureTier(region, "1000000.00", "100000", TIER_PRICE);
    final String id = startCheckout(shopper, SMALL);
    assertTrue(step1(id, shopper, region).body().containsKey("step1Complete"));
    final Called evaluated = evaluateAs(id, shopper);
    assertEquals("PAYMENT_ALLOWED", evaluated.body().get("stage"));
    assertEquals(TIER_PRICE, amountText(((Map<?, ?>) evaluated.body().get("quote")).get("cost")));

    // 5. the methods on offer, a gateway method chosen, the overview read
    assertTrue(
        ((List<?>) methodsAs(id, shopper).body().get("methods"))
            .stream().anyMatch(row -> CARD.equals(((Map<?, ?>) row).get("method"))));
    assertEquals(200, selectAs(id, shopper, CARD).status());
    final String shown = totalAs(id, shopper);

    // 6. payment against the success stub
    final Called paid = payAs(id, shopper);
    assertEquals(200, paid.status());
    assertEquals("CAPTURED", paid.body().get(RESULT));
    final String number = (String) paid.body().get("orderReference");
    assertEquals(OrderStatus.PAID, orderRepo.findByNumber(number).block().status());

    // 7. the confirmation, and exactly one mail to each side
    final Called confirmation = confirmAs(id, shopper);
    assertEquals("PAID", confirmation.body().get("kind"));
    assertEquals(number, confirmation.body().get("orderNumber"));
    assertTrue(BR6_NUMBER.matcher(number).matches(), "BR-6 shaped: " + number);
    assertEquals(1, mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, number).size());
    assertEquals(1, mailsOf(MailTemplate.ORDER_NOTIFICATION_STAFF, number).size());

    // 8. the history shows the order with its total and status
    final Called listed = history(shopper);
    assertEquals(1, ((List<?>) listed.body().get("orders")).size(), "one order, one number");
    assertEquals("PAID", statusInHistory(shopper, number));
    final Called detail = historyOf(shopper, number);
    assertEquals(shown, amountText(((Map<?, ?>) detail.body().get(TOTALS)).get("grandTotal")));

    // the invariants that span the flow
    assertEquals(br5(detail), new BigDecimal(shown), "BR-5, computed here from the order's lines");
    assertEquals(
        List.of(br5(detail)), chargedAmounts(), "the provider was asked for exactly that total");
    assertEquals(
        new BigDecimal(TIER_PRICE),
        amount(((Map<?, ?>) detail.body().get(TOTALS)).get("delivery")));
  }

  @Test
  void laterPriceChangeDoesNotChangeThePaidOrder() {
    final Shopper shopper = shopper();
    stubAuthorise(GATEWAY, CAPTURED_STUB);
    final String id = readyWithin(shopper, nextRegion());
    final String number = (String) payAs(id, shopper).body().get("orderReference");
    final BigDecimal before =
        amount(((Map<?, ?>) historyOf(shopper, number).body().get(TOTALS)).get("grandTotal"));

    seed.observeGold("95.00");

    final Called after = historyOf(shopper, number);
    assertEquals(before, amount(((Map<?, ?>) after.body().get(TOTALS)).get("grandTotal")), "BR-2");
    assertEquals(List.of(before), chargedAmounts());
    final Called catalog = call(HttpMethod.GET, "/api/catalog/products/" + SMALL, null, null, null);
    assertNotNull(catalog.body().get("price"), "the catalog price did follow the new reference");
  }

  @Test
  void declinedPaymentKeepsEverythingAndLeavesTheOrderAwaitingPayment() {
    final Shopper shopper = shopper();
    stubAuthorise(GATEWAY, DECLINED_STUB);
    final String id = readyWithin(shopper, nextRegion());

    final Called declined = payAs(id, shopper);

    assertEquals(200, declined.status());
    assertEquals("DECLINED", declined.body().get(RESULT));
    final Map<?, ?> session = session(id, shopper.token()).body();
    assertEquals(1, ((Map<?, ?>) session.get("basket")).get("itemCount"), "the basket survives");
    assertNotNull(session.get("details"), "step 1 survives");
    assertNotNull(
        ((Map<?, ?>) session.get("delivery")).get("quote"), "the delivery quote survives");
    assertEquals(CARD, methodsAs(id, shopper).body().get("selected"), "the method survives");
    final String number = (String) declined.body().get("orderReference");
    assertEquals(OrderStatus.AWAITING_PAYMENT, orderRepo.findByNumber(number).block().status());
    assertEquals("AWAITING_PAYMENT", statusInHistory(shopper, number));
    assertNull(confirmAs(id, shopper).body().get("kind"), "a declined order is not confirmed");
    assertEquals(0, mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, number).size());
  }

  private static String amountText(Object price) {
    return (String) ((Map<?, ?>) price).get("amount");
  }
}
