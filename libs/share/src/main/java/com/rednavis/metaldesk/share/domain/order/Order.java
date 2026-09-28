package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.AddressKind;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.payment.PaymentRecord;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The order aggregate root (Architecture section 3): who ordered what, where it goes, and where it
 * stands.
 *
 * <p>It composes; it does not orchestrate. It holds the immutable {@link OrderLine} snapshots that
 * make price finality true (BRD BR-2), and it delegates its totals to {@link OrderTotalsCalculator}
 * (BR-5). It has <strong>no</strong> methods that move it between statuses: which moves are legal
 * is the {@link OrderStateMachine}, and a method here would put a second copy of that table in the
 * wrong place. Status is data on the order; changing it is done by building the order that results.
 *
 * <p>The quote and the payment are optional because they are genuinely absent early: a freshly
 * created order has neither.
 *
 * <p>An order has at least one line, and the delivery address is a delivery address (not a billing
 * one). The list of lines is copied defensively and is unmodifiable.
 *
 * @param id the order's identifier, never null
 * @param number the human-readable number (BRD BR-6), never null
 * @param customerId the customer who ordered, never null
 * @param deliveryAddress where the order is delivered, never null and of kind {@link
 *     AddressKind#DELIVERY}
 * @param lines the order lines, never null or empty and holding no null element
 * @param quote the delivery quote, or empty when none has been made yet; never null
 * @param payment the payment record, or empty when none exists yet; never null
 * @param status where the order is in its life, never null
 * @param createdAt when the order was created, never null
 * @param updatedAt when the order last changed, never null and not before {@code createdAt}
 */
public record Order(
    OrderId id,
    OrderNumber number,
    CustomerId customerId,
    Address deliveryAddress,
    List<OrderLine> lines,
    Optional<DeliveryQuote> quote,
    Optional<PaymentRecord> payment,
    OrderStatus status,
    Instant createdAt,
    Instant updatedAt) {

  /**
   * Validates the fields and copies the line list.
   *
   * @throws ValidationException if a field is null, the lines are empty or hold a null, the address
   *     is not a delivery address, or {@code updatedAt} is before {@code createdAt}
   */
  public Order {
    requireIdentity(id, number, customerId, status);
    requireDeliveryAddress(deliveryAddress);
    requireLines(lines);
    lines = List.copyOf(lines);
    requireOptionals(quote, payment);
    requireTimestamps(createdAt, updatedAt);
  }

  private static void requireIdentity(
      OrderId id, OrderNumber number, CustomerId customerId, OrderStatus status) {
    if (id == null || number == null || customerId == null || status == null) {
      throw new ValidationException(
          "order.field-missing", "Order requires an id, number, customer and status");
    }
  }

  private static void requireDeliveryAddress(Address deliveryAddress) {
    if (deliveryAddress == null || deliveryAddress.kind() != AddressKind.DELIVERY) {
      throw new ValidationException("order.address-invalid", "Order requires a delivery address");
    }
  }

  private static void requireLines(List<OrderLine> lines) {
    if (lines == null || lines.isEmpty() || lines.stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(
          "order.lines-invalid", "Order needs at least one line and no null line");
    }
  }

  private static void requireOptionals(
      Optional<DeliveryQuote> quote, Optional<PaymentRecord> payment) {
    if (quote == null || payment == null) {
      throw new ValidationException(
          "order.optional-missing", "Order quote and payment must be an Optional, not null");
    }
  }

  private static void requireTimestamps(Instant createdAt, Instant updatedAt) {
    if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
      throw new ValidationException(
          "order.timestamps-invalid", "Order needs createdAt, and updatedAt not before it");
    }
  }

  /**
   * Creates a new order: {@link OrderStatus#CREATED}, with neither a quote nor a payment, whose
   * creation and last-change instants are both the one given.
   *
   * @param id the order's identifier
   * @param number the order number
   * @param customerId the customer who ordered
   * @param deliveryAddress where the order is delivered
   * @param lines the order lines
   * @param now when the order is created
   * @return the new order
   * @throws ValidationException if the order would be invalid
   */
  public static Order created(
      OrderId id,
      OrderNumber number,
      CustomerId customerId,
      Address deliveryAddress,
      List<OrderLine> lines,
      Instant now) {
    return new Order(
        id,
        number,
        customerId,
        deliveryAddress,
        lines,
        Optional.empty(),
        Optional.empty(),
        OrderStatus.CREATED,
        now,
        now);
  }

  /**
   * Computes the order's totals (BRD BR-5) from its line snapshots and its quote.
   *
   * <p>Until a delivery quote exists the delivery cost is zero, so the total of an unquoted order
   * is the total of its goods and tax only. Nothing is read from live catalog or tax data.
   *
   * @return net, tax, delivery and grand total
   * @throws ValidationException if the lines or the quote are in different currencies
   */
  public OrderTotals totals() {
    final Money delivery =
        quote
            .map(DeliveryQuote::cost)
            .orElseGet(() -> Money.zero(lines.get(0).lineNet().currency()));
    return OrderTotalsCalculator.compute(lines, delivery);
  }

  /**
   * Counts the units ordered, as order history shows them (BRD FR-10.1).
   *
   * @return the sum of the line quantities
   */
  public int itemCount() {
    return lines.stream().mapToInt(line -> line.quantity().value()).sum();
  }
}
