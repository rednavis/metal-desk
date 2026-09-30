package com.rednavis.metaldesk.api.checkout.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * The out-of-band return of a redirect payment (BRD FR-7.2): a claim to be verified with the
 * provider, never evidence. A callback that says "success" is worth nothing if the provider says
 * otherwise.
 */
class PaymentCallbackTest extends PaymentTestSupport {

  private static final String CAPTURED = "CAPTURED";
  private static final String REF_5 = "gw_cb_5";
  private static final String REF_6 = "gw_cb_6";

  private static final String CALLBACK_PATH = "/api/checkout/payment/callback";
  private static final String RESULT = "result";

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  private String pendingRedirect(String reference) {
    stubAuthorise(
        GATEWAY,
        "{\"status\":\"redirect_required\",\"reference\":\""
            + reference
            + "\",\"redirectUrl\":\"https://pay.example/s/"
            + reference
            + "\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.BANK_REDIRECT);
    pay(id);
    return id;
  }

  private Called callback(String id, String reference, String extra) {
    return call(
        HttpMethod.GET,
        CALLBACK_PATH + "?checkout=" + id + "&reference=" + reference + extra,
        null,
        null,
        null);
  }

  @Test
  void callbackTheProviderConfirmsMarksTheOrderPaid() {
    final String id = pendingRedirect("gw_cb_1");
    stubConfirm("gw_cb_1", "{\"status\":\"captured\",\"reference\":\"gw_cb_1\"}");

    final Called result = callback(id, "gw_cb_1", "");

    assertEquals(200, result.status());
    assertEquals(CAPTURED, result.body().get(RESULT));
    assertEquals(OrderStatus.PAID, orderOf(result).status());
    assertEquals(
        1,
        settlement.paidOrders().stream()
            .filter(o -> o.number().format().equals(result.body().get("orderReference")))
            .count());
  }

  @Test
  void callbackClaimingSuccessIsNotBelievedWhenTheProviderSaysOtherwise() {
    final String id = pendingRedirect("gw_cb_2");
    stubConfirm("gw_cb_2", "{\"status\":\"declined\",\"declineCode\":\"insufficient_funds\"}");

    final Called result = callback(id, "gw_cb_2", "&status=success&paid=true&result=CAPTURED");

    assertEquals("DECLINED", result.body().get(RESULT));
    assertEquals(OrderStatus.AWAITING_PAYMENT, orderOf(result).status(), "not paid");
    assertEquals(
        0,
        settlement.paidOrders().stream()
            .filter(o -> o.number().format().equals(result.body().get("orderReference")))
            .count());
  }

  @Test
  void declinedConfirmationReturnsTheCustomerToRetryableCheckout() {
    final String id = pendingRedirect("gw_cb_3");
    stubConfirm("gw_cb_3", "{\"status\":\"declined\",\"declineCode\":\"instrument_refused\"}");
    callback(id, "gw_cb_3", "");
    providers().resetAll();
    stubAuthorise(GATEWAY, "{\"status\":\"captured\",\"reference\":\"gw_cb_3b\"}");

    final Called retried = pay(id);

    assertEquals(CAPTURED, retried.body().get(RESULT));
  }

  @Test
  void callbackForTheWrongReferenceOrAnUnknownCheckoutIsRefusedAndAsksNoProvider() {
    final String id = pendingRedirect("gw_cb_4");
    providers().resetRequests();

    final Called wrongReference = callback(id, "somebody-elses", "&status=success");
    final Called unknownCheckout = callback("no-such-checkout", "gw_cb_4", "");
    final Called noParameters = call(HttpMethod.GET, CALLBACK_PATH, null, null, null);

    for (final Called refused : new Called[] {wrongReference, unknownCheckout, noParameters}) {
      assertEquals(400, refused.status());
      assertEquals("payment.callback-invalid", refused.body().get("code"));
    }
    providers()
        .verify(0, WireMock.postRequestedFor(WireMock.urlPathMatching(GATEWAY + "/.*/confirm")));
  }

  @Test
  void callbackForSessionWithNothingPendingIsRefused() {
    stubAuthorise(GATEWAY, "{\"status\":\"declined\",\"declineCode\":\"insufficient_funds\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    pay(id);

    assertEquals(400, callback(id, "anything", "").status());
  }

  @Test
  void repeatedCallbackAfterPaymentReportsCapturedWithoutAskingTheProviderAgain() {
    final String id = pendingRedirect(REF_5);
    stubConfirm(REF_5, "{\"status\":\"captured\",\"reference\":\"gw_cb_5\"}");
    callback(id, REF_5, "");

    final Called again = callback(id, REF_5, "");

    assertEquals(CAPTURED, again.body().get(RESULT));
    providers()
        .verify(
            1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(GATEWAY + "/gw_cb_5/confirm")));
  }

  @Test
  void providerThatCannotConfirmLeavesThePaymentPendingAndReportsAnError() {
    final String id = pendingRedirect(REF_6);
    providers()
        .stubFor(
            WireMock.post(WireMock.urlPathEqualTo(GATEWAY + "/gw_cb_6/confirm"))
                .willReturn(WireMock.serverError()));

    final Called result = callback(id, REF_6, "");

    assertEquals("ERROR", result.body().get(RESULT));
    assertEquals(OrderStatus.AWAITING_PAYMENT, orderOf(result).status());
    stubConfirm(REF_6, "{\"status\":\"captured\",\"reference\":\"gw_cb_6\"}");
    assertEquals(CAPTURED, callback(id, REF_6, "").body().get(RESULT));
  }

  @Test
  void theCallbackAlsoAcceptsPost() {
    final String id = pendingRedirect("gw_cb_7");
    stubConfirm("gw_cb_7", "{\"status\":\"captured\",\"reference\":\"gw_cb_7\"}");

    final Called result =
        call(
            HttpMethod.POST,
            CALLBACK_PATH + "?checkout=" + id + "&reference=gw_cb_7",
            null,
            null,
            null);

    assertEquals(CAPTURED, result.body().get(RESULT));
  }
}
