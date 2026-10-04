package com.rednavis.metaldesk.api.checkout.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.cart.CartStore;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * Whatever way an order gets paid for, the cart it was made from is gone afterwards, so the
 * customer's next add starts a fresh one. A payment that did not go through keeps the cart.
 */
class PaymentClearsCartTest extends PaymentTestSupport {

  private static final String CAPTURED = "CAPTURED";
  private static final String CAPTURED_BODY = "{\"status\":\"captured\",\"reference\":\"gw_c_1\"}";
  private static final String DECLINED_BODY =
      "{\"status\":\"declined\",\"declineCode\":\"insufficient_funds\"}";
  private static final String CALLBACK_PATH = "/api/checkout/payment/callback";

  @Autowired private CartStore carts;
  @Autowired private CheckoutSessionStore sessions;

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  private static Object resultOf(Called called) {
    return called.body().get("result");
  }

  private boolean cartExists(String checkoutId) {
    return Boolean.TRUE.equals(
        sessions
            .find(checkoutId)
            .flatMap(session -> carts.find(session.cart().orElseThrow().value()))
            .hasElement()
            .block());
  }

  private String ready(PaymentMethod method) {
    final String id = readyCheckout(SMALL, 1, method);
    assertTrue(cartExists(id), "the cart exists before paying");
    return id;
  }

  @Test
  void capturedCardPaymentClearsTheCart() {
    stubAuthorise(GATEWAY, CAPTURED_BODY);
    final String id = ready(PaymentMethod.CARD);

    assertEquals(CAPTURED, resultOf(pay(id)));

    assertFalse(cartExists(id));
  }

  @Test
  void invoicePaymentClearsTheCart() {
    final String id = ready(PaymentMethod.INVOICE);

    assertEquals("DOCUMENT_ISSUED", resultOf(pay(id)));

    assertFalse(cartExists(id));
  }

  @Test
  void redirectPaymentClearsTheCartAndStaysClearedAfterTheCallbackConfirms() {
    stubAuthorise(
        GATEWAY,
        "{\"status\":\"redirect_required\",\"reference\":\"gw_c_2\","
            + "\"redirectUrl\":\"https://pay.example/s/gw_c_2\"}");
    final String id = ready(PaymentMethod.BANK_REDIRECT);

    assertEquals("REDIRECT", resultOf(pay(id)));
    assertFalse(cartExists(id));

    stubConfirm("gw_c_2", "{\"status\":\"captured\",\"reference\":\"gw_c_2\"}");
    final Called confirmed =
        call(
            HttpMethod.GET,
            CALLBACK_PATH + "?checkout=" + id + "&reference=gw_c_2",
            null,
            null,
            null);
    assertEquals(CAPTURED, resultOf(confirmed));
    assertFalse(cartExists(id));
  }

  @Test
  void declinedPaymentKeepsTheCartAndTheRetryClearsIt() {
    stubAuthorise(GATEWAY, DECLINED_BODY);
    final String id = ready(PaymentMethod.CARD);

    assertEquals("DECLINED", resultOf(pay(id)));
    assertTrue(cartExists(id));

    providers().resetAll();
    stubAuthorise(GATEWAY, CAPTURED_BODY);
    assertEquals(CAPTURED, resultOf(pay(id)));
    assertFalse(cartExists(id));
  }
}
