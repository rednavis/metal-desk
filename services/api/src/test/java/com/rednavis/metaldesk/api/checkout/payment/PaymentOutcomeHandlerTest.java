package com.rednavis.metaldesk.api.checkout.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.cart.CartStore;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentResultView;
import com.rednavis.metaldesk.payments.provider.DeclineReason;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentStatus;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Every {@code PaymentOutcome} variant is handled (BRD FR-7.2), each on an order of its own that a
 * decline has left awaiting payment. {@code ElementRequired}, which no bundled adapter produces, is
 * covered here directly.
 */
class PaymentOutcomeHandlerTest extends PaymentTestSupport {

  private static final String PROVIDER = "test-provider";

  @Autowired private PaymentOutcomeHandler handler;
  @Autowired private PaymentOrders orders;
  @Autowired private CartStore carts;
  @Autowired private CheckoutSessionStore sessions;

  private String checkout;
  private Order order;

  @BeforeEach
  void awaitingPayment() {
    seedPaymentCatalog();
    stubAuthorise(GATEWAY, "{\"status\":\"declined\",\"declineCode\":\"insufficient_funds\"}");
    checkout = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    final Called declined = pay(checkout);
    order =
        orders
            .find(new com.rednavis.metaldesk.share.domain.id.OrderId(orderOf(declined).id()))
            .block();
  }

  private PaymentResultView handle(PaymentOutcome outcome) {
    return handler.handle(checkout, order, PROVIDER, PaymentMethod.CARD, outcome).block();
  }

  private Order stored() {
    return orders.find(order.id()).block();
  }

  private boolean cartExists() {
    return Boolean.TRUE.equals(
        sessions
            .find(checkout)
            .flatMap(session -> carts.find(session.cart().orElseThrow().value()))
            .hasElement()
            .block());
  }

  private static ProviderReference reference() {
    return new ProviderReference("ref-1");
  }

  @Test
  void captured() {
    final PaymentResultView view = handle(new PaymentOutcome.Captured(reference()));

    assertEquals("CAPTURED", view.result());
    assertEquals(OrderStatus.PAID, stored().status());
    assertEquals(PaymentStatus.CAPTURED, stored().payment().orElseThrow().status());
    assertFalse(cartExists());
  }

  @Test
  void redirectRequired() {
    final PaymentResultView view =
        handle(
            new PaymentOutcome.RedirectRequired(reference(), URI.create("https://pay.example/x")));

    assertEquals("REDIRECT", view.result());
    assertEquals("https://pay.example/x", view.redirectUrl());
    assertFalse(cartExists());
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored().status());
    assertEquals(PaymentStatus.PENDING, stored().payment().orElseThrow().status());
  }

  @Test
  void elementRequired() {
    final PaymentResultView view =
        handle(new PaymentOutcome.ElementRequired(reference(), "secret-handle"));

    assertEquals("ELEMENT", view.result());
    assertEquals("secret-handle", view.clientHandle());
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored().status());
    assertEquals(PaymentStatus.PENDING, stored().payment().orElseThrow().status());
    assertFalse(view.toString().contains("secret-handle"));
  }

  @Test
  void documentIssued() {
    final PaymentResultView view = handle(new PaymentOutcome.DocumentIssued(reference()));

    assertEquals("DOCUMENT_ISSUED", view.result());
    assertEquals("ref-1", view.invoiceReference());
    assertFalse(cartExists());
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored().status());
    assertEquals(PaymentStatus.PENDING, stored().payment().orElseThrow().status());
  }

  @Test
  void declined() {
    final PaymentResultView view =
        handle(new PaymentOutcome.Declined(DeclineReason.RISK_BLOCKED, Optional.empty()));

    assertEquals("DECLINED", view.result());
    assertEquals("RISK_BLOCKED", view.declineReason());
    assertNotNull(view.message());
    assertTrue(cartExists());
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored().status());
  }

  @Test
  void failed() {
    final PaymentResultView view = handle(new PaymentOutcome.Failed("Please try again."));

    assertEquals("ERROR", view.result());
    assertEquals("payment.failed", view.errorCode());
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored().status());
  }

  @Test
  void providerThatCannotBeUsedIsAnErrorNotDecline() {
    final PaymentResultView view =
        handler
            .unavailable(
                checkout,
                order,
                PROVIDER,
                new PaymentProviderException(
                    PaymentProviderException.Kind.UNREACHABLE, "down", null))
            .block();

    assertEquals("ERROR", view.result());
    assertEquals("payment.provider-unavailable", view.errorCode());
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored().status());
  }
}
