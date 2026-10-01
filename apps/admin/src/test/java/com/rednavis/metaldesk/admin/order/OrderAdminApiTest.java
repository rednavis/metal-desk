package com.rednavis.metaldesk.admin.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.admin.AdminTestSupport;
import com.rednavis.metaldesk.admin.persistence.OrderStore;
import com.rednavis.metaldesk.persistence.fixtures.OrderFixtures;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.order.OrderTransitions;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentRecord;
import com.rednavis.metaldesk.share.domain.payment.PaymentStatus;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** The statuses staff drive, in order, and only in order. */
class OrderAdminApiTest extends AdminTestSupport {

  private static final String ORDERS = "/api/admin/orders/";
  private static final String ID = "o-1";
  private static final String SUMMARY_STATUS = "$.summary.status";
  private static final String SHIPMENT =
      "{\"carrier\":\"DHL\",\"trackingReference\":\"JD0146000012345\"}";

  @Autowired private OrderStore store;

  private void paid() {
    saveCustomer();
    save(OrderFixtures.paidOrder(ID, OrderFixtures.number(1)));
  }

  private void invoiced(PaymentMethod method) {
    saveCustomer();
    final Order paid = OrderFixtures.paidOrder(ID, OrderFixtures.number(1));
    final PaymentRecord captured = paid.payment().orElseThrow();
    save(
        new Order(
            paid.id(),
            paid.number(),
            paid.customerId(),
            paid.deliveryAddress(),
            paid.lines(),
            paid.quote(),
            Optional.of(
                new PaymentRecord(
                    "invoice",
                    method,
                    PaymentStatus.PENDING,
                    new ProviderReference("INV-1"),
                    captured.amount())),
            OrderStatus.AWAITING_PAYMENT,
            paid.createdAt(),
            paid.updatedAt()));
  }

  private ResponseSpec fulfil() {
    return post(ORDERS + ID + "/fulfillment");
  }

  private ResponseSpec ship(String json) {
    return put(ORDERS + ID + "/shipment", json);
  }

  private OrderStatus stored() {
    return orderMapper.toDomain(orders.findById(ID).orElseThrow()).status();
  }

  @Test
  void orderGoesFromPaidToDeliveredInOrder() {
    paid();

    fulfil().expectStatus().isOk().expectBody().jsonPath(SUMMARY_STATUS).isEqualTo("FULFILLING");
    ship(SHIPMENT)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(SUMMARY_STATUS)
        .isEqualTo("SHIPPED")
        .jsonPath("$.shipment.carrier")
        .isEqualTo("DHL")
        .jsonPath("$.shipment.trackingReference")
        .isEqualTo("JD0146000012345");
    post(ORDERS + ID + "/delivery")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(SUMMARY_STATUS)
        .isEqualTo("DELIVERED")
        .jsonPath("$.actions")
        .isEmpty();

    assertEquals(OrderStatus.DELIVERED, stored());
    assertEquals("DHL", shipments.findById(ID).orElseThrow().carrier());
  }

  @Test
  void invoicePaymentReceivedMakesTheOrderPaidAndFulfillable() {
    invoiced(PaymentMethod.INVOICE);

    post(ORDERS + ID + "/payment-received")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(SUMMARY_STATUS)
        .isEqualTo("PAID")
        .jsonPath("$.payment.status")
        .isEqualTo("CAPTURED")
        .jsonPath("$.payment.reference")
        .isEqualTo("INV-1");

    assertEquals(OrderStatus.PAID, stored());
    fulfil().expectStatus().isOk();
  }

  @Test
  void paymentReceivedIsRefusedWithoutPendingInvoice() {
    invoiced(PaymentMethod.CARD);

    post(ORDERS + ID + "/payment-received").expectStatus().isEqualTo(409);
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored());
  }

  @Test
  void paymentReceivedIsRefusedOnOrderThatIsAlreadyPaid() {
    paid();

    post(ORDERS + ID + "/payment-received").expectStatus().isEqualTo(409);
    assertEquals(OrderStatus.PAID, stored());
  }

  @Test
  void stepsOutOfOrderAreRefusedByTheStateMachine() {
    paid();

    post(ORDERS + ID + "/delivery").expectStatus().isEqualTo(409);
    assertEquals(OrderStatus.PAID, stored());

    fulfil().expectStatus().isOk();
    fulfil().expectStatus().isEqualTo(409);
    post(ORDERS + ID + "/delivery").expectStatus().isEqualTo(409);
    assertEquals(OrderStatus.FULFILLING, stored());
  }

  @Test
  void shipmentCannotBeEnteredForOrderThatIsNotBeingFulfilled() {
    paid();

    ship(SHIPMENT).expectStatus().isEqualTo(409);

    assertEquals(OrderStatus.PAID, stored());
    assertTrue(shipments.findById(ID).isEmpty());
  }

  @Test
  void invalidShipmentChangesNothing() {
    paid();
    fulfil().expectStatus().isOk();

    ship("{\"carrier\":\" \",\"trackingReference\":\"X1\"}").expectStatus().isBadRequest();
    ship("{\"carrier\":\"DHL\"}").expectStatus().isBadRequest();
    ship("{\"carrier\":\"DHL\",\"trackingReference\":\"a\\u0000b\"}").expectStatus().isBadRequest();

    assertEquals(OrderStatus.FULFILLING, stored());
    assertTrue(shipments.findById(ID).isEmpty());
  }

  @Test
  void statusChangeIsRefusedIfOrderMovedUnderneath() {
    paid();
    final Order stale = store.require(ID);
    final Order mine = OrderTransitions.advance(stale, TransitionTrigger.FULFILLMENT_STARTED, NOW);
    store.advance(stale, mine);

    assertThrows(ConflictException.class, () -> store.advance(stale, mine));
    assertEquals(OrderStatus.FULFILLING, stored());
  }

  @Test
  void ordersAreListedAndFilteredByStatus() {
    paid();
    save(orderAfter("o-2", 2, TransitionTrigger.CHECKOUT_SUBMITTED));

    get("/api/admin/orders").expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(2);
    get("/api/admin/orders?status=PAID")
        .expectBody()
        .jsonPath("$.total")
        .isEqualTo(1)
        .jsonPath("$.items[0].id")
        .isEqualTo(ID);
    get("/api/admin/orders?size=0").expectStatus().isBadRequest();
    get("/api/admin/orders?status=BOGUS").expectStatus().isBadRequest();
  }

  @Test
  void detailShowsWhatStaffNeed() {
    paid();

    get(ORDERS + ID)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.contact.name")
        .isEqualTo("Ann Example")
        .jsonPath("$.lines.length()")
        .isEqualTo(2)
        .jsonPath("$.quote.cost")
        .isEqualTo("12.50")
        .jsonPath("$.actions[0]")
        .isEqualTo("FULFILLMENT_STARTED");
    get(ORDERS + "nope").expectStatus().isNotFound();
  }
}
