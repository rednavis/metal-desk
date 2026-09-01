package com.rednavis.metaldesk.api.checkout.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.rednavis.metaldesk.persistence.document.OrderDocument;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Executing a payment through the provider abstraction (BRD FR-7.2), each outcome in turn, and the
 * retention of everything the customer entered when it does not succeed (FR-6.3).
 */
class PaymentExecutionTest extends PaymentTestSupport {

  private static final String DETAILS = "details";
  private static final String DELIVERY = "delivery";
  private static final String SELECTED = "selected";
  private static final String CAPTURED = "CAPTURED";
  private static final String ORDER_REFERENCE = "orderReference";

  private static final String RESULT = "result";
  private static final String CODE = "code";
  private static final String CAPTURED_BODY =
      "{\"status\":\"captured\",\"reference\":\"gw_cap_1\"}";
  private static final String DECLINED =
      "{\"status\":\"declined\",\"declineCode\":\"insufficient_funds\"}";

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  private OrderDocument order(Called result) {
    return orderOf(result);
  }

  @Test
  void capturedPaymentIsRecordedAndTheOrderIsPaidThroughTheStateMachine() {
    stubAuthorise(GATEWAY, CAPTURED_BODY);
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);

    final Called result = pay(id);

    assertEquals(200, result.status());
    assertEquals(CAPTURED, result.body().get(RESULT));
    final OrderDocument order = order(result);
    assertEquals(OrderStatus.PAID, order.status());
    assertEquals("gw_cap_1", order.payment().reference());
    assertEquals(PaymentStatus.CAPTURED, order.payment().status());
    assertEquals("gateway", order.payment().providerId());
    assertEquals(PaymentMethod.CARD, order.payment().method());
    assertEquals(totalOf(id), order.payment().amount().amount());
    assertEquals(
        1,
        settlement.paidOrders().stream()
            .filter(paid -> paid.number().format().equals(order.number()))
            .count());
  }

  @Test
  void theAmountTheProviderIsAskedToChargeIsTheOverviewGrandTotal() {
    stubAuthorise(GATEWAY, CAPTURED_BODY);
    final String id = readyCheckout(SMALL, 2, PaymentMethod.CARD);
    final String shown = totalOf(id);

    final Called result = pay(id);

    final List<LoggedRequest> requests =
        providers().findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(GATEWAY)));
    assertEquals(1, requests.size());
    final String body = requests.get(0).getBodyAsString();
    assertTrue(body.contains("\"amount\":\"" + shown + "\""), body + " should charge " + shown);
    assertTrue(body.contains("\"currency\":\"EUR\""), body);
    assertTrue(body.contains(order(result).id()), body);
  }

  @Test
  void theOverviewComputesBr5AndShowsEveryComponent() {
    final String id = readyCheckout(TAXED, 1, PaymentMethod.INVOICE);

    final Called called = overview(id);

    assertEquals(200, called.status());
    final Map<?, ?> totals = (Map<?, ?>) called.body().get("totals");
    final java.math.BigDecimal net = amount(totals.get("net"));
    final java.math.BigDecimal tax = amount(totals.get("tax"));
    final java.math.BigDecimal delivery = amount(totals.get(DELIVERY));
    assertEquals(net.add(tax).add(delivery), amount(totals.get("grandTotal")));
    assertEquals(new java.math.BigDecimal("10.00"), delivery);
    assertEquals("INVOICE", called.body().get("paymentMethod"));
    assertEquals(1, ((List<?>) called.body().get("lines")).size());
    assertNotNull(((Map<?, ?>) called.body().get(DETAILS)).get("street"));
    assertNotNull(called.body().get(DELIVERY));
  }

  private static java.math.BigDecimal amount(Object price) {
    return new java.math.BigDecimal((String) ((Map<?, ?>) price).get("amount"));
  }

  @Test
  void redirectLeavesTheOrderAwaitingPaymentWithPendingRecord() {
    stubAuthorise(
        GATEWAY,
        "{\"status\":\"redirect_required\",\"reference\":\"gw_red_1\",\"redirectUrl\":\"https://pay.example/s/gw_red_1\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.BANK_REDIRECT);

    final Called result = pay(id);

    assertEquals("REDIRECT", result.body().get(RESULT));
    assertEquals("https://pay.example/s/gw_red_1", result.body().get("redirectUrl"));
    final OrderDocument order = order(result);
    assertEquals(OrderStatus.AWAITING_PAYMENT, order.status());
    assertEquals(PaymentStatus.PENDING, order.payment().status());
    assertEquals("gw_red_1", order.payment().reference());
  }

  @Test
  void invoiceLeavesTheOrderAwaitingPaymentWithPendingInvoiceAndCallsNoProvider() {
    final String id = readyCheckout(GOLD_1, 1, PaymentMethod.INVOICE);

    final Called result = pay(id);

    assertEquals("DOCUMENT_ISSUED", result.body().get(RESULT));
    assertNotNull(result.body().get("invoiceReference"));
    final OrderDocument order = order(result);
    assertEquals(OrderStatus.AWAITING_PAYMENT, order.status());
    assertEquals(PaymentStatus.PENDING, order.payment().status());
    assertEquals(PaymentMethod.INVOICE, order.payment().method());
    assertEquals(result.body().get("invoiceReference"), order.payment().reference());
    assertTrue(
        invoices.documents().stream().findAny().isPresent(),
        "the rendered invoice went to the sink");
    assertEquals(
        1,
        settlement.invoicedOrders().stream()
            .filter(o -> o.number().format().equals(order.number()))
            .count());
    providers().verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
  }

  @Test
  void walletAccountPaymentGoesToTheWalletProviderNotTheGateway() {
    stubAuthorise(WALLET, "{\"status\":\"captured\",\"reference\":\"wl_cap_1\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.WALLET_ACCOUNT);

    final Called result = pay(id);

    assertEquals(CAPTURED, result.body().get(RESULT));
    assertEquals("wallet", order(result).payment().providerId());
    providers().verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(WALLET)));
    providers().verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo(GATEWAY)));
  }

  @Test
  void declineReturnsToSelectionWithEverythingIntactAndTheOrderStillAwaitingPayment() {
    stubAuthorise(GATEWAY, DECLINED);
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);

    final Called result = pay(id);

    assertEquals(200, result.status());
    assertEquals("DECLINED", result.body().get(RESULT));
    assertEquals("INSUFFICIENT_FUNDS", result.body().get("declineReason"));
    assertNotNull(result.body().get("message"));
    assertEquals(1, ((Map<?, ?>) session(id, null).body().get("basket")).get("itemCount"));
    assertNotNull(session(id, null).body().get(DETAILS));
    assertNotNull(((Map<?, ?>) session(id, null).body().get(DELIVERY)).get("quote"));
    assertEquals("CARD", methods(id).body().get(SELECTED));
    assertEquals(OrderStatus.AWAITING_PAYMENT, order(result).status(), "never cancelled");
  }

  @Test
  void retryAfterDeclineReusesTheSameOrder() {
    stubAuthorise(GATEWAY, DECLINED);
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    final Called declined = pay(id);
    providers().resetAll();
    stubAuthorise(GATEWAY, CAPTURED_BODY);

    final Called retried = pay(id);

    assertEquals(CAPTURED, retried.body().get(RESULT));
    assertEquals(declined.body().get(ORDER_REFERENCE), retried.body().get(ORDER_REFERENCE));
    assertEquals(OrderStatus.PAID, order(retried).status());
  }

  @Test
  void theCustomerCanSwitchMethodAfterDecline() {
    stubAuthorise(GATEWAY, DECLINED);
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    final Called declined = pay(id);
    stubAuthorise(WALLET, "{\"status\":\"captured\",\"reference\":\"wl_cap_2\"}");
    select(id, PaymentMethod.WALLET_ACCOUNT);

    final Called retried = pay(id);

    assertEquals(CAPTURED, retried.body().get(RESULT));
    assertEquals(declined.body().get(ORDER_REFERENCE), retried.body().get(ORDER_REFERENCE));
  }

  @Test
  void timeoutIsNotReportedAsDeclineAndRetainsEverything() {
    stubSlow(GATEWAY);
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);

    final Called result = pay(id);

    assertEquals(200, result.status());
    assertEquals("ERROR", result.body().get(RESULT));
    assertEquals("payment.provider-unavailable", result.body().get("errorCode"));
    assertNull(result.body().get("declineReason"), "a timeout is not a decline");
    assertTrue(
        result
            .body()
            .get("message")
            .toString()
            .contains((String) result.body().get(ORDER_REFERENCE)));
    assertEquals(OrderStatus.AWAITING_PAYMENT, order(result).status());
    assertEquals("CARD", methods(id).body().get(SELECTED));
    assertNotNull(session(id, null).body().get(DETAILS));
    assertNotNull(((Map<?, ?>) session(id, null).body().get(DELIVERY)).get("quote"));
  }

  @Test
  void failedStatusIsAnActionableErrorWithTheSameRetention() {
    stubAuthorise(GATEWAY, "{\"status\":\"failed\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);

    final Called result = pay(id);

    assertEquals("ERROR", result.body().get(RESULT));
    assertEquals("payment.failed", result.body().get("errorCode"));
    assertNotNull(result.body().get("message"));
    assertEquals(OrderStatus.AWAITING_PAYMENT, order(result).status());
    assertEquals("CARD", methods(id).body().get(SELECTED));
  }

  @Test
  void responseThatCannotBeUnderstoodIsProviderErrorNotDecline() {
    stubAuthorise(GATEWAY, "{\"status\":\"mystery\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);

    final Called result = pay(id);

    assertEquals("ERROR", result.body().get(RESULT));
    assertEquals("payment.provider-error", result.body().get("errorCode"));
    assertEquals(OrderStatus.AWAITING_PAYMENT, order(result).status());
  }

  @Test
  void payingWithoutConfirmingTotalIsFieldError() {
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);

    final Called missing =
        call(
            org.springframework.http.HttpMethod.POST,
            SESSIONS + "/" + id + "/payment/execute",
            null,
            null,
            Map.of());
    final Called malformed = pay(id, "lots");

    assertEquals(400, missing.status());
    assertEquals("validation.failed", missing.body().get(CODE));
    assertEquals(400, malformed.status());
    providers().verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
  }

  @Test
  void totalThatIsNotTheOneTheCustomerSawIsRefusedAndNoOrderIsCreated() {
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);

    final Called result = pay(id, "1.00");

    assertEquals(409, result.status());
    assertEquals("checkout.total-changed", result.body().get(CODE));
    providers().verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    assertNull(overview(id).body().get(ORDER_REFERENCE), "no order was created");
  }

  @Test
  void payingWithoutMethodIsRefused() {
    final String region = nextRegion();
    configureTier(region, "1000000.00", "100000", "10.00");
    final String id = checkoutTo(region, SMALL, 1);
    evaluate(id);

    final Called result = pay(id, totalOf(id));

    assertEquals(409, result.status());
    assertEquals("checkout.method-not-selected", result.body().get(CODE));
  }

  @Test
  void paidOrderCannotBePaidTwice() {
    stubAuthorise(GATEWAY, CAPTURED_BODY);
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    pay(id);

    final Called again = pay(id);

    assertEquals(409, again.status());
    assertEquals("checkout.already-paid", again.body().get(CODE));
    providers().verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(GATEWAY)));
  }

  @Test
  void secondPaymentWhileRedirectIsPendingIsRefused() {
    stubAuthorise(
        GATEWAY,
        "{\"status\":\"redirect_required\",\"reference\":\"gw_red_2\",\"redirectUrl\":\"https://pay.example/s/gw_red_2\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.BANK_REDIRECT);
    pay(id);

    final Called again = pay(id);

    assertEquals(409, again.status());
    assertEquals("checkout.payment-in-progress", again.body().get(CODE));
    providers().verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(GATEWAY)));
  }

  @Test
  void simultaneousPaymentsCreateOneOrderAndChargeOnce() {
    stubAuthorise(GATEWAY, CAPTURED_BODY);
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    final String total = totalOf(id);

    final List<Called> calls =
        java.util.stream.IntStream.range(0, 4).parallel().mapToObj(i -> pay(id, total)).toList();

    providers().verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(GATEWAY)));
    assertEquals(1, calls.stream().filter(call -> call.status() == 200).count());
  }
}
