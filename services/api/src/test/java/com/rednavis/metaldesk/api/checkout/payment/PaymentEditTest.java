package com.rednavis.metaldesk.api.checkout.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Going back to edit an earlier step invalidates everything computed from it (BRD FR-7.1). */
class PaymentEditTest extends PaymentTestSupport {

  private static final String CODE = "code";

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  @Test
  void editingStepOneClearsTheEvaluationAndPaymentIsRefusedUntilItIsRepeated() {
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    assertEquals(200, methods(id).status());

    final Called edited = submit(id, null, with(formSameCountry(id), "city", "Hamburg"));

    assertEquals(200, edited.status());
    assertNull(session(id, null).body().get("delivery"));
    final Called refused = pay(id, "1.00");
    assertEquals(409, refused.status());
    assertEquals("checkout.delivery-not-evaluated", refused.body().get(CODE));
    assertEquals(409, methods(id).status());
    evaluate(id);
    assertEquals(200, methods(id).status());
    assertEquals("CARD", methods(id).body().get("selected"), "the chosen method is kept");
  }

  @Test
  void editingAfterDeclineCancelsTheOldOrderAndTheNextPaymentCreatesFreshOne() {
    stubAuthorise(GATEWAY, "{\"status\":\"declined\",\"declineCode\":\"insufficient_funds\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    final Called declined = pay(id);
    final String firstOrder = (String) declined.body().get("orderReference");

    submit(id, null, with(formSameCountry(id), "city", "Hamburg"));

    assertEquals(OrderStatus.CANCELLED, orderOf(declined).status());
    evaluate(id);
    providers().resetAll();
    stubAuthorise(GATEWAY, "{\"status\":\"captured\",\"reference\":\"gw_edit_1\"}");
    final Called paid = pay(id);
    assertEquals("CAPTURED", paid.body().get("result"));
    assertNotEquals(firstOrder, paid.body().get("orderReference"));
    assertEquals("Hamburg", orderOf(paid).deliveryAddress().city());
  }

  @Test
  void whileRedirectPaymentIsPendingEarlierStepsAreLocked() {
    stubAuthorise(
        GATEWAY,
        "{\"status\":\"redirect_required\",\"reference\":\"gw_edit_2\",\"redirectUrl\":\"https://pay.example/s/gw_edit_2\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.BANK_REDIRECT);
    pay(id);

    final Called edit = submit(id, null, formSameCountry(id));

    assertEquals(409, edit.status());
    assertEquals("checkout.payment-in-progress", edit.body().get(CODE));
  }

  @Test
  void onceAnInvoiceIsIssuedEarlierStepsAreLocked() {
    final String id = readyCheckout(GOLD_1, 1, PaymentMethod.INVOICE);
    pay(id);

    assertEquals(409, submit(id, null, formSameCountry(id)).status());
    assertEquals(409, select(id, PaymentMethod.INVOICE).status());
  }

  @Test
  void theMethodCannotBeChangedWhilePaymentIsPending() {
    stubAuthorise(
        GATEWAY,
        "{\"status\":\"redirect_required\",\"reference\":\"gw_edit_3\",\"redirectUrl\":\"https://pay.example/s/gw_edit_3\"}");
    final String id = readyCheckout(SMALL, 1, PaymentMethod.BANK_REDIRECT);
    pay(id);

    final Called change = select(id, PaymentMethod.CARD);

    assertEquals(409, change.status());
  }
}
