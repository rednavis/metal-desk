package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.CheckoutProperties;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.api.checkout.delivery.BasketMetrics;
import com.rednavis.metaldesk.api.checkout.delivery.CheckoutStage;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryState;
import com.rednavis.metaldesk.api.checkout.step1.ConsentRecord;
import com.rednavis.metaldesk.api.checkout.step1.CustomerDetails;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.PhoneNumber;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.NotFoundException;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Lets a customer pay an order that is still awaiting payment, from the order itself.
 *
 * <p>An order is paid through a checkout session, and sessions expire, so the session that created
 * an order may be gone. This starts a new one <em>around the existing order</em>: its lines, its
 * delivery address and quote, and a payment state bound to the order, so the ordinary payment path
 * charges that very order at the total it carries and never creates a second one. Nothing is priced
 * again. The session does not come from the cart (it is of the {@code BUY_NOW} kind, which leaves
 * the cart alone), and its details are not editable, because editing step 1 would detach the order.
 *
 * <p>Another customer's order is {@code 404}, as in the order history.
 */
@Service
@RequiredArgsConstructor
public class PaymentResumeService {

  private final OrderRepository orders;
  private final OrderMapper orderMapper;
  private final CustomerRepository customers;
  private final CheckoutSessionStore store;
  private final BasketMetrics metrics;
  private final CheckoutProperties properties;
  private final Clock clock;

  /**
   * Starts a payment session for one of the caller's orders.
   *
   * @param customer the signed-in customer
   * @param number the order number
   * @return the session, ready for a payment method
   * @throws NotFoundException {@code order.not-found} if it is not the caller's
   * @throws ConflictException {@code order.not-payable} if it is not awaiting payment
   */
  public Mono<CheckoutSession> resume(AuthenticatedCustomer customer, String number) {
    return orders
        .findByNumber(number)
        .filter(document -> document.customerId().equals(customer.id().value()))
        .switchIfEmpty(Mono.error(() -> new NotFoundException("order.not-found", "No such order")))
        .map(orderMapper::toDomain)
        .map(PaymentResumeService::requirePayable)
        .flatMap(order -> start(customer, order));
  }

  private static Order requirePayable(Order order) {
    if (order.status() != OrderStatus.AWAITING_PAYMENT || order.quote().isEmpty()) {
      throw new ConflictException("order.not-payable", "This order is not awaiting payment");
    }
    return order;
  }

  private Mono<CheckoutSession> start(AuthenticatedCustomer customer, Order order) {
    return customers
        .findById(customer.id().value())
        .switchIfEmpty(Mono.error(() -> new NotFoundException("order.not-found", "No such order")))
        .map(owner -> detailsOf(owner, order))
        // The weight is shown, not charged on; an order must stay payable if a product has left the
        // catalog since.
        .zipWith(metrics.weight(order.lines()).onErrorReturn(Weight.of("0", WeightUnit.GRAM)))
        .flatMap(
            pair ->
                store
                    .create(
                        Optional.of(customer.id()),
                        CheckoutSession.Source.BUY_NOW,
                        Optional.empty(),
                        order.lines())
                    .flatMap(
                        created ->
                            store.update(
                                created.id(),
                                current -> bind(current, order, pair.getT1(), pair.getT2()))));
  }

  private CheckoutSession bind(
      CheckoutSession session, Order order, CustomerDetails details, Weight weight) {
    final DeliveryQuote quote = order.quote().orElseThrow();
    final DeliveryState delivery =
        new DeliveryState(
            CheckoutStage.PAYMENT_ALLOWED,
            order.quote(),
            Optional.empty(),
            metrics.exTaxValue(order.lines()),
            weight,
            quote.quotedAt());
    return session
        .withStep1(
            details,
            new ConsentRecord(true, properties.policyVersion(), clock.instant()),
            Optional.empty())
        .withQuotedHandoff(delivery, order.id());
  }

  private static CustomerDetails detailsOf(CustomerDocument owner, Order order) {
    if (owner.phone() == null) {
      throw new ConflictException(
          "order.payment-details-missing", "Add a phone number to your account to pay this order");
    }
    return new CustomerDetails(
        owner.name(),
        new EmailAddress(owner.email()),
        new PhoneNumber(owner.phone()),
        order.deliveryAddress(),
        Optional.empty());
  }
}
