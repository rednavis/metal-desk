package com.rednavis.metaldesk.api.checkout.confirmation;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionService;
import com.rednavis.metaldesk.api.checkout.confirmation.dto.ConfirmationKind;
import com.rednavis.metaldesk.api.checkout.confirmation.dto.ConfirmationView;
import com.rednavis.metaldesk.api.checkout.delivery.HandoffRecord;
import com.rednavis.metaldesk.api.checkout.payment.PaymentOrders;
import com.rednavis.metaldesk.api.checkout.payment.PaymentState;
import com.rednavis.metaldesk.api.order.OrderStatusLabels;
import com.rednavis.metaldesk.api.order.OrderViews;
import com.rednavis.metaldesk.api.web.OperationFailedException;
import com.rednavis.metaldesk.payments.invoice.InvoiceNumber;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.DomainException;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Closes the checkout (BRD FR-8.1): tells both parties about the order and shows the confirmation,
 * on <strong>either</strong> success path, a captured or invoiced payment or a manager handoff.
 *
 * <p>It allocates no order number. The order already has one: the payment step created the order
 * before calling the provider, and a handoff creates it with the number that is the customer's
 * reference (T-036), so there is exactly one number per order whichever path it took.
 *
 * <p>Confirming is idempotent. The mails are recorded per order and template, so confirming twice
 * sends one pair. If sending fails, the failure is reported with a code, the order number and a
 * correlation id that is also in the error log, and confirming again sends what is still missing.
 */
@Service
@RequiredArgsConstructor
public class ConfirmationService {

  /** The code of the answer when a confirmation could not be completed. */
  public static final String FAILED = "checkout.confirmation-failed";

  /** The code of the answer when the session has no placed order to confirm. */
  public static final String NOT_CONFIRMABLE = "checkout.not-confirmable";

  private final CheckoutSessionService sessions;
  private final PaymentOrders orders;
  private final OrderNotificationService notifications;

  /**
   * Confirms a checkout: sends what has not been sent and returns the confirmation.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, or null for a guest
   * @return the confirmation
   * @throws ConflictException {@code checkout.not-confirmable} if no order was placed
   * @throws OperationFailedException {@code checkout.confirmation-failed} if the mails could not be
   *     sent
   */
  public Mono<ConfirmationView> confirm(String id, AuthenticatedCustomer customer) {
    return sessions
        .find(id, customer)
        .flatMap(
            session ->
                placedOrder(session)
                    .flatMap(
                        order ->
                            settle(order, localeOf(session))
                                .then(Mono.fromSupplier(() -> view(order)))))
        .switchIfEmpty(Mono.error(ConfirmationService::notConfirmable));
  }

  /**
   * Sends what an order is owed on being placed, without checking who asks.
   *
   * @param order a paid, invoiced or handed-off order
   * @param locale the customer's language
   * @return a signal that completes when everything was sent or had been
   * @throws OperationFailedException {@code checkout.confirmation-failed} on a failed send
   */
  public Mono<Void> settle(Order order, Locale locale) {
    return notify(order, locale)
        .onErrorMap(
            failure -> !(failure instanceof DomainException),
            failure ->
                new OperationFailedException(
                    FAILED,
                    "Your order "
                        + order.number().format()
                        + " was placed, but we could not send its confirmation. Please try again,"
                        + " or contact support and quote the order number and this error's"
                        + " reference.",
                    order.number().format(),
                    failure));
  }

  private Mono<Void> notify(Order order, Locale locale) {
    return switch (kindOf(order).orElse(null)) {
      case PAID -> notifications.orderPlaced(order, locale);
      case INVOICE ->
          notifications.orderPlaced(order, locale).then(notifications.invoiceIssued(order, locale));
      case MANAGER_QUOTE -> Mono.empty();
      case null -> Mono.error(notConfirmable());
    };
  }

  private Mono<Order> placedOrder(CheckoutSession session) {
    final Optional<OrderId> handoff = session.handoff().map(HandoffRecord::orderId);
    final Optional<OrderId> paying = session.payment().flatMap(PaymentState::order);
    return Mono.justOrEmpty(handoff.or(() -> paying)).flatMap(orders::find);
  }

  private static Locale localeOf(CheckoutSession session) {
    return session.payment().map(PaymentState::locale).orElse(Locale.ENGLISH);
  }

  private static ConfirmationView view(Order order) {
    final ConfirmationKind kind = kindOf(order).orElseThrow(ConfirmationService::notConfirmable);
    final boolean quoted = kind != ConfirmationKind.MANAGER_QUOTE;
    return new ConfirmationView(
        kind,
        order.number().format(),
        order.status().name(),
        OrderStatusLabels.of(order.status()),
        order.itemCount(),
        quoted ? OrderViews.price(order.totals().grandTotal()) : null,
        kind == ConfirmationKind.INVOICE ? InvoiceNumber.forOrder(order.number()).value() : null,
        message(kind));
  }

  /** Which way an order was placed, or empty if it has not been placed. */
  private static Optional<ConfirmationKind> kindOf(Order order) {
    final Optional<ConfirmationKind> kind;
    if (order.status() == OrderStatus.PAID) {
      kind = Optional.of(ConfirmationKind.PAID);
    } else if (order.status() == OrderStatus.AWAITING_MANAGER_QUOTE) {
      kind = Optional.of(ConfirmationKind.MANAGER_QUOTE);
    } else if (order.status() == OrderStatus.AWAITING_PAYMENT
        && order
            .payment()
            .filter(payment -> payment.method() == PaymentMethod.INVOICE)
            .isPresent()) {
      kind = Optional.of(ConfirmationKind.INVOICE);
    } else {
      kind = Optional.empty();
    }
    return kind;
  }

  private static String message(ConfirmationKind kind) {
    return switch (kind) {
      case PAID -> "Thank you. Your payment was received and we will let you know when it ships.";
      case INVOICE -> "Thank you. Your invoice was emailed to you; we ship once it is paid.";
      case MANAGER_QUOTE ->
          "Thank you. A manager will send you a price and terms for this order by email.";
    };
  }

  private static ConflictException notConfirmable() {
    return new ConflictException(NOT_CONFIRMABLE, "There is no placed order to confirm");
  }
}
