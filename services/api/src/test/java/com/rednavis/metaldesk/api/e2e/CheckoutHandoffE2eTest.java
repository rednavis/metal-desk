package com.rednavis.metaldesk.api.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.persistence.repository.ManagerQuoteRepository;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.persistence.document.ManagerQuoteDocument;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * The manager-handoff path of BRD FR-5.3, end to end across two services: the customer's side goes
 * through this application's HTTP surface, the staff side through the real {@code apps/admin},
 * started in its own class loader against the same database and called over HTTP with the proxy's
 * identity header (see {@link AdminHarnessTestSupport}). The order is never touched by anything but
 * these two surfaces.
 *
 * <p>What is covered of admin: its web layer, identity check, state-machine transition and
 * persistence, for the queue and for setting terms or declining. Not covered here: the mail admin
 * sends to the customer, which goes to admin's own in-process sender and is asserted in admin's own
 * tests.
 */
class CheckoutHandoffE2eTest extends E2eTestSupport {

  private static final String STATUS = "status";
  private static final String HUMAN_PRICE = "250.00";
  private static final String TIER_PRICE = "12.50";
  private static final String CAPTURED_STUB =
      "{\"status\":\"captured\",\"reference\":\"gw_e2e_h\"}";
  private static final String CODE = "code";

  @Autowired private ManagerQuoteRepository quotes;

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  /** A checkout whose basket is over the value ceiling of its region's only tier. */
  private String overTheCeiling(Shopper shopper) {
    final String region = nextRegion();
    configureTier(region, "100.00", "100000", TIER_PRICE);
    final String id = startCheckout(shopper, SMALL);
    assertTrue(step1(id, shopper, region).body().containsKey("step1Complete"));
    return id;
  }

  private Called attemptToPay(String id, Shopper shopper) {
    return call(
        HttpMethod.POST,
        SESSIONS + "/" + id + "/payment/execute",
        null,
        shopper.token(),
        Map.of("confirmedTotal", "1.00"));
  }

  private AdminHarnessTestSupport.Answer queueEntry(String number)
      throws IOException, InterruptedException {
    final AdminHarnessTestSupport.Answer queue =
        admin().call("GET", "/api/admin/quotes?size=100", null);
    assertEquals(200, queue.status());
    return new AdminHarnessTestSupport.Answer(
        200,
        ((List<?>) queue.body().get("items"))
            .stream()
                .map(item -> (Map<?, ?>) item)
                .filter(item -> number.equals(item.get("number")))
                .findFirst()
                .map(item -> Map.<String, Object>of("id", item.get("id")))
                .orElseThrow(() -> new AssertionError("order " + number + " is not in the queue")));
  }

  private AdminHarnessTestSupport.Answer putTerms(String number)
      throws IOException, InterruptedException {
    final String orderId = (String) queueEntry(number).body().get("id");
    return admin()
        .call(
            "POST",
            "/api/admin/quotes/" + orderId + "/terms",
            ("{\"deliveryPrice\":\"%s\",\"terms\":\"Insured courier, signature required.\","
                    + "\"transitMinDays\":3,\"transitMaxDays\":5,\"validUntil\":\"%s\"}")
                .formatted(HUMAN_PRICE, Instant.now().plusSeconds(86_400L * 30)));
  }

  private OrderStatus stored(String number) {
    return Objects.requireNonNull(orderRepo.findByNumber(number).block()).status();
  }

  @Test
  void handedOffOrderIsPricedByStaffThenPaidAtTheirPrice()
      throws IOException, InterruptedException {
    final Shopper shopper = shopper();
    final String id = overTheCeiling(shopper);

    final String number = handOff(id, shopper);
    assertWaitingForStaff(id, shopper, number);

    // staff, through the real admin application, set the terms
    final AdminHarnessTestSupport.Answer quoted = putTerms(number);
    assertEquals(200, quoted.status(), quoted.body().toString());
    assertEquals("AWAITING_PAYMENT", quoted.body().get(STATUS));
    assertEquals(HUMAN_PRICE, quoted.body().get("deliveryPrice"));
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored(number));
    assertEquals("AWAITING_PAYMENT", statusInHistory(shopper, number));

    payAtTheHumanPrice(id, shopper, number);
  }

  /** Evaluates, proves payment is refused before and after the handoff, and hands off. */
  private String handOff(String id, Shopper shopper) {
    final Called evaluated = evaluateAs(id, shopper);
    assertEquals("HANDOFF_REQUIRED", evaluated.body().get("stage"));
    assertEquals("VALUE", evaluated.body().get("boundCeiling"));

    // FR-5.3: no inline payment, before or after the handoff, and the provider is never reached
    final Called before = attemptToPay(id, shopper);
    assertEquals(409, before.status());
    assertEquals("checkout.handoff-required", before.body().get(CODE));
    final Called handoff = handoffAs(id, shopper);
    assertEquals(200, handoff.status());
    assertEquals(409, attemptToPay(id, shopper).status());
    assertEquals(0, providerRequests(), "the payment provider saw nothing");
    return (String) handoff.body().get("reference");
  }

  /** The order waits for a quote, the customer is told, and staff learn which ceiling bound. */
  private void assertWaitingForStaff(String id, Shopper shopper, String number) {
    assertEquals(OrderStatus.AWAITING_MANAGER_QUOTE, stored(number));
    assertEquals("AWAITING_MANAGER_QUOTE", statusInHistory(shopper, number));
    assertEquals("MANAGER_QUOTE", confirmAs(id, shopper).body().get("kind"));
    assertEquals(1, mailsOf(MailTemplate.HANDOFF_RECEIPT_CUSTOMER, number).size());
    final List<TransactionalMail> toStaff =
        mailsOf(MailTemplate.HANDOFF_NOTIFICATION_STAFF, number);
    assertEquals(1, toStaff.size());
    assertTrue(toStaff.get(0).body().contains("Ceiling exceeded: value"), toStaff.get(0).body());
  }

  /** The customer pays the very order staff priced, and what is charged is read from the stub. */
  private void payAtTheHumanPrice(String id, Shopper shopper, String number) {
    assertEquals("PAYMENT_ALLOWED", evaluateAs(id, shopper).body().get("stage"));
    assertEquals(200, selectAs(id, shopper, "CARD").status());
    final Called detail = historyOf(shopper, number);
    final BigDecimal expected = br5(detail);
    assertEquals(
        new BigDecimal(HUMAN_PRICE),
        amount(((Map<?, ?>) detail.body().get("totals")).get("delivery")));
    assertEquals(
        expected,
        new BigDecimal(totalAs(id, shopper)),
        "the overview shows BR-5 at the human price");
    stubAuthorise(GATEWAY, CAPTURED_STUB);
    final Called paid = payAs(id, shopper);
    assertEquals("CAPTURED", paid.body().get("result"));
    assertEquals(number, paid.body().get("orderReference"), "the handed-off order was paid");

    assertEquals(List.of(expected), chargedAmounts());
    assertEquals(OrderStatus.PAID, stored(number));
    assertEquals("PAID", statusInHistory(shopper, number));
    assertEquals("PAID", confirmAs(id, shopper).body().get("kind"));
    assertEquals(1, mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, number).size());
    assertEquals(1, ((List<?>) history(shopper).body().get("orders")).size(), "one order number");
  }

  @Test
  void staffDecliningCancelsTheOrderAndNothingCanBeCharged()
      throws IOException, InterruptedException {
    final Shopper shopper = shopper();
    final String id = overTheCeiling(shopper);
    evaluateAs(id, shopper);
    final String number = (String) handoffAs(id, shopper).body().get("reference");
    final String orderId = (String) queueEntry(number).body().get("id");

    final AdminHarnessTestSupport.Answer declined =
        admin().call("POST", "/api/admin/quotes/" + orderId + "/decline", null);

    assertEquals(200, declined.status());
    assertEquals("CANCELLED", declined.body().get(STATUS));
    assertEquals(OrderStatus.CANCELLED, stored(number));
    assertEquals("CANCELLED", statusInHistory(shopper, number));
    assertEquals(409, attemptToPay(id, shopper).status());
    assertEquals(0, providerRequests());
  }

  @Test
  void termsThatHaveExpiredAreNotChargedOn() throws IOException, InterruptedException {
    final Shopper shopper = shopper();
    final String id = overTheCeiling(shopper);
    evaluateAs(id, shopper);
    final String number = (String) handoffAs(id, shopper).body().get("reference");
    assertEquals(200, putTerms(number).status());
    final String orderId = Objects.requireNonNull(orderRepo.findByNumber(number).block()).id();
    final ManagerQuoteDocument terms = Objects.requireNonNull(quotes.findById(orderId).block());
    quotes
        .save(
            new ManagerQuoteDocument(
                terms.orderId(),
                terms.finalPrice(),
                terms.terms(),
                terms.quotedAt(),
                Instant.now().minusSeconds(60),
                terms.staff()))
        .block();
    stubAuthorise(GATEWAY, CAPTURED_STUB);

    final Called refused = attemptToPay(id, shopper);

    assertEquals(409, refused.status());
    assertEquals("checkout.quote-expired", refused.body().get(CODE));
    assertEquals(0, providerRequests());
  }
}
