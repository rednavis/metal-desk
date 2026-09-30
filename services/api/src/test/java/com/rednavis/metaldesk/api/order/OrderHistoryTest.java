package com.rednavis.metaldesk.api.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.persistence.document.ShipmentDocument;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** The order history of a signed-in customer (BRD FR-10.1). */
class OrderHistoryTest extends OrderHistoryTestSupport {

  private static final String ORDERS = "/api/orders";
  private static final String STATUS = "status";

  @Test
  void everyStatusHasCustomerFacingLabel() {
    for (final OrderStatus status : OrderStatus.values()) {
      assertFalse(OrderStatusLabels.of(status).isBlank(), status + " has no label");
    }
  }

  @Test
  void listReturnsNumberDateItemCountTotalAndStatusForEveryOrderOfTheCaller() {
    final Caller caller = caller();
    for (final OrderStatus status : OrderStatus.values()) {
      placeOrder(caller, status);
    }

    final Called listed = call(HttpMethod.GET, ORDERS, null, caller.token(), null);

    assertEquals(200, listed.status());
    final List<Map<String, Object>> rows = rows(listed);
    assertEquals(OrderStatus.values().length, rows.size());
    for (final Map<String, Object> row : rows) {
      assertNotNull(row.get("orderNumber"));
      assertNotNull(row.get("createdAt"));
      assertEquals(5, row.get("itemCount"));
      assertNotNull(((Map<?, ?>) row.get("total")).get("amount"));
      assertFalse(((String) row.get("statusLabel")).isBlank());
    }
    assertEquals(
        Arrays.stream(OrderStatus.values()).map(Enum::name).sorted().toList(),
        rows.stream().map(row -> (String) row.get(STATUS)).sorted().toList());
  }

  @Test
  void historyTotalDoesNotMoveWhenTheProductPriceChangesAfterTheOrder() {
    final Caller caller = caller();
    final Order order = placeOrder(caller, OrderStatus.PAID);
    final String before = totalOf(call(HttpMethod.GET, ORDERS, null, caller.token(), null));
    assertEquals(order.totals().grandTotal().amount().toPlainString(), before);

    seed.pricedProduct(
        "gold-bar",
        "Gold bar 1 oz",
        seed.category("hist-cat", TaxCategory.INVESTMENT_GRADE),
        "500");

    assertEquals(before, totalOf(call(HttpMethod.GET, ORDERS, null, caller.token(), null)));
    final Called detail =
        call(HttpMethod.GET, ORDERS + "/" + order.number().format(), null, caller.token(), null);
    assertEquals(
        before,
        ((Map<?, ?>) ((Map<?, ?>) detail.body().get("totals")).get("grandTotal")).get("amount"));
  }

  @Test
  void shipmentIsAbsentBeforeShippingEvenIfOneIsRecorded() {
    final Caller caller = caller();
    final Order order = placeOrder(caller, OrderStatus.AWAITING_PAYMENT);
    shipments.save(new ShipmentDocument(order.id().value(), "DHL", "TRACK-1")).block();

    final Called detail =
        call(HttpMethod.GET, ORDERS + "/" + order.number().format(), null, caller.token(), null);

    assertEquals(200, detail.status());
    assertFalse(detail.body().containsKey("shipment"));
  }

  @Test
  void shipmentAppearsOnceShipped() {
    final Caller caller = caller();
    final Order order = placeOrder(caller, OrderStatus.SHIPPED);
    shipments.save(new ShipmentDocument(order.id().value(), "DHL", "TRACK-2")).block();

    final Called detail =
        call(HttpMethod.GET, ORDERS + "/" + order.number().format(), null, caller.token(), null);

    assertEquals(
        Map.of("carrier", "DHL", "trackingReference", "TRACK-2"), detail.body().get("shipment"));
  }

  @Test
  void shippedOrderWithNoShipmentYetHasNoShipmentField() {
    final Caller caller = caller();
    final Order order = placeOrder(caller, OrderStatus.SHIPPED);

    final Called detail =
        call(HttpMethod.GET, ORDERS + "/" + order.number().format(), null, caller.token(), null);

    assertFalse(detail.body().containsKey("shipment"));
  }

  @Test
  void anotherCustomersOrderIsNotFoundInTheListOrTheDetail() {
    final Caller owner = caller();
    final Caller other = caller();
    final Order order = placeOrder(owner, OrderStatus.PAID);

    final Called detail =
        call(HttpMethod.GET, ORDERS + "/" + order.number().format(), null, other.token(), null);
    final Called listed = call(HttpMethod.GET, ORDERS, null, other.token(), null);

    assertEquals(404, detail.status());
    assertEquals("order.not-found", detail.body().get("code"));
    assertTrue(rows(listed).isEmpty());
    assertEquals(
        404,
        call(HttpMethod.GET, ORDERS + "/000000000000", null, other.token(), null).status(),
        "an order that does not exist looks the same");
  }

  @Test
  void historyNeedsSignedInCustomer() {
    assertEquals(401, call(HttpMethod.GET, ORDERS, null, null, null).status());
  }
}
