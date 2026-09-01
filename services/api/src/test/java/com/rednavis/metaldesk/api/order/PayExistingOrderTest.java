package com.rednavis.metaldesk.api.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/** Paying an order that is awaiting payment, from the order itself. */
class PayExistingOrderTest extends OrderHistoryTestSupport {

  private static final String ORDERS = "/api/orders/";
  private static final String PAYMENT_SESSION = "/payment-session";

  @Autowired private CustomerRepository customers;

  private Caller callerWithPhone() {
    final Caller caller = caller();
    final CustomerDocument stored =
        Objects.requireNonNull(customers.findById(caller.id().value()).block());
    customers
        .save(
            new CustomerDocument(
                stored.id(),
                stored.name(),
                stored.email(),
                "+49 30 1234567",
                stored.addresses(),
                stored.verification()))
        .block();
    return caller;
  }

  @Test
  void customerWithoutPhoneIsToldToAddOne() {
    final Caller caller = caller();
    final Order order = placeAwaitingPayment(caller);

    final Called refused =
        call(
            HttpMethod.POST,
            ORDERS + order.number().format() + PAYMENT_SESSION,
            null,
            caller.token(),
            null);

    assertEquals(409, refused.status());
    assertEquals("order.payment-details-missing", refused.body().get("code"));
  }

  @Test
  void awaitingOrderGetsCheckoutThatChargesThatSameOrder() {
    final Caller caller = callerWithPhone();
    final Order order = placeAwaitingPayment(caller);

    final Called started =
        call(
            HttpMethod.POST,
            ORDERS + order.number().format() + PAYMENT_SESSION,
            null,
            caller.token(),
            null);

    assertEquals(200, started.status(), String.valueOf(started.body()));
    final String checkoutId = (String) started.body().get("checkoutId");
    assertNotNull(checkoutId);
    final String sessions = "/api/checkout/sessions/" + checkoutId;
    assertEquals(
        200,
        call(HttpMethod.GET, sessions + "/payment/methods", null, caller.token(), null).status());
    final Called overview =
        call(HttpMethod.GET, sessions + "/overview", null, caller.token(), null);
    assertEquals(200, overview.status());
    final Map<?, ?> totals = (Map<?, ?>) overview.body().get("totals");
    assertEquals(
        order.totals().grandTotal().amount().toPlainString(),
        ((Map<?, ?>) totals.get("grandTotal")).get("amount"));
  }

  @Test
  void anotherCustomersOrderIsNotFound() {
    final Order order = placeAwaitingPayment(caller());

    final Called refused =
        call(
            HttpMethod.POST,
            ORDERS + order.number().format() + PAYMENT_SESSION,
            null,
            caller().token(),
            null);

    assertEquals(404, refused.status());
  }

  @Test
  void anOrderThatIsNotAwaitingPaymentCannotBePaid() {
    final Caller caller = caller();
    final Order paid = placeOrder(caller, OrderStatus.PAID);

    final Called refused =
        call(
            HttpMethod.POST,
            ORDERS + paid.number().format() + PAYMENT_SESSION,
            null,
            caller.token(),
            null);

    assertEquals(409, refused.status());
    assertEquals("order.not-payable", refused.body().get("code"));
  }

  @Test
  void needsSignedInCustomer() {
    assertEquals(
        401,
        call(HttpMethod.POST, ORDERS + "000000000000" + PAYMENT_SESSION, null, null, null)
            .status());
  }
}
