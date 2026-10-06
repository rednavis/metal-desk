package com.rednavis.metaldesk.share.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.AddressKind;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderValidationTest {

  private static List<OrderLine> lines() {
    return List.of(OrderFixtures.goldLine(), OrderFixtures.silverLine());
  }

  @Test
  void emptyLinesAreRefused() {
    assertEquals(
        "order.lines-invalid",
        assertThrows(ValidationException.class, () -> OrderFixtures.createdOrder(List.of()))
            .code());
  }

  @Test
  void nonDeliveryAddressIsRefused() {
    final Address billing =
        new Address(
            AddressKind.BILLING, "1 Main Street", "Berlin", new Region("de"), "10115", null, null);
    final List<OrderLine> lines = lines();
    assertEquals(
        "order.address-invalid",
        assertThrows(
                ValidationException.class,
                () ->
                    Order.created(
                        new OrderId("o-1"),
                        new OrderNumber(LocalDate.of(2022, 2, 8), 4),
                        new CustomerId("c-1"),
                        billing,
                        lines,
                        OrderFixtures.CREATED_AT))
            .code());
  }

  @Test
  void updatedBeforeCreatedIsRefused() {
    final Order created = OrderFixtures.createdOrder(lines());
    assertEquals(
        "order.timestamps-invalid",
        assertThrows(
                ValidationException.class,
                () ->
                    new Order(
                        created.id(),
                        created.number(),
                        created.customerId(),
                        created.deliveryAddress(),
                        created.lines(),
                        Optional.empty(),
                        Optional.empty(),
                        OrderStatus.CREATED,
                        created.createdAt(),
                        created.createdAt().minusSeconds(1)))
            .code());
  }

  @Test
  void nullOptionalsAreRefused() {
    final Order created = OrderFixtures.createdOrder(lines());
    assertEquals(
        "order.optional-missing",
        assertThrows(
                ValidationException.class,
                () ->
                    new Order(
                        created.id(),
                        created.number(),
                        created.customerId(),
                        created.deliveryAddress(),
                        created.lines(),
                        null,
                        Optional.empty(),
                        OrderStatus.CREATED,
                        created.createdAt(),
                        created.updatedAt()))
            .code());
  }
}
