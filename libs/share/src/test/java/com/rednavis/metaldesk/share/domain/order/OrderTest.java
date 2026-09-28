package com.rednavis.metaldesk.share.domain.order;

import static com.rednavis.metaldesk.share.domain.order.OrderFixtures.eur;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.payment.PaymentRecord;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderTest {

  private static List<OrderLine> lines() {
    return List.of(OrderFixtures.goldLine(), OrderFixtures.silverLine());
  }

  private static Order withQuote(DeliveryQuote quote) {
    final Order created = OrderFixtures.createdOrder(lines());
    return new Order(
        created.id(),
        created.number(),
        created.customerId(),
        created.deliveryAddress(),
        created.lines(),
        Optional.of(quote),
        Optional.of(new PaymentRecord(eur("4261.94"))),
        OrderStatus.AWAITING_PAYMENT,
        created.createdAt(),
        created.updatedAt());
  }

  @Test
  void createdOrderHasNeitherQuoteNorPayment() {
    final Order order = OrderFixtures.createdOrder(lines());
    assertEquals(OrderStatus.CREATED, order.status());
    assertEquals(Optional.empty(), order.quote());
    assertEquals(Optional.empty(), order.payment());
  }

  @Test
  void totalsBeforeQuoteHaveZeroDelivery() {
    final OrderTotals totals = OrderFixtures.createdOrder(lines()).totals();
    assertEquals(eur("0.00"), totals.delivery());
    assertEquals(eur("4236.94"), totals.grandTotal());
  }

  @Test
  void totalsUseTheQuotedDeliveryCost() {
    final OrderTotals totals = withQuote(new DeliveryQuote(eur("25.00"))).totals();
    assertEquals(eur("25.00"), totals.delivery());
    assertEquals(eur("4261.94"), totals.grandTotal());
  }

  @Test
  void itemCountSumsTheQuantities() {
    assertEquals(5, OrderFixtures.createdOrder(lines()).itemCount());
  }

  @Test
  void totalsAreStableWhenTheSourceListChangesAfterwards() {
    final List<OrderLine> source = new ArrayList<>(lines());
    final Order order = OrderFixtures.createdOrder(source);
    final OrderTotals before = order.totals();
    source.clear();
    assertEquals(2, order.lines().size());
    assertEquals(before, order.totals());
  }

  @Test
  void orderExposesNoStatusChangingMethod() {
    final boolean changesStatus =
        Arrays.stream(Order.class.getDeclaredMethods())
            .map(Method::getName)
            .anyMatch(
                name ->
                    name.startsWith("mark")
                        || name.startsWith("transition")
                        || name.startsWith("set")
                        || name.startsWith("with")
                        || "cancel".equals(name));
    assertFalse(changesStatus);
  }
}
