package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.account.LocaleParser;
import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionService;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.api.checkout.OrderTransitions;
import com.rednavis.metaldesk.api.persistence.OrderNumberSequence;
import com.rednavis.metaldesk.api.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * The manager handoff (BRD FR-5.3, BR-10): route an order the tiers cannot price to staff, confirm
 * receipt to the customer with a reference number, and take no payment.
 *
 * <p><strong>The handoff creates the order.</strong> The state machine reaches {@code
 * AWAITING_MANAGER_QUOTE} only from an order awaiting payment, so the order is created, submitted
 * and then moved by the {@code TIER_EXCEEDED} trigger through {@link OrderStateMachine}; nothing
 * here assigns a status. The order number is allocated the usual way (BR-6) and <strong>is the
 * reference</strong> the customer is given, so there is one number to quote.
 *
 * <p>The tiers are evaluated again first, live: if staff have widened one since the customer saw
 * the handoff action and the order now fits, the handoff is refused (409) and the customer pays
 * instead. It is idempotent: a session that already has a handoff returns it and sends nothing
 * more. Of two simultaneous requests only the one whose claim on the session wins creates the
 * order; the loser has spent an order number, and gaps are allowed (see {@code
 * OrderNumberSequence}).
 *
 * <p>A crash after the session is claimed and before the order is saved would leave a handoff
 * reference without an order; the order id in the session record is what a reconciliation would
 * look for.
 */
@Service
@RequiredArgsConstructor
public class HandoffService {

  private final CheckoutSessionService sessions;
  private final CheckoutSessionStore store;
  private final DeliveryEvaluationService evaluation;
  private final HandoffCustomers customers;
  private final OrderNumberSequence numbers;
  private final OrderRepository orders;
  private final OrderMapper orderMapper;
  private final HandoffMails mails;
  private final Clock clock;

  /**
   * Hands a checkout to staff.
   *
   * @param id the session's id
   * @param customer the signed-in customer, or null for a guest
   * @param locale the language tag of the customer's confirmation, or null
   * @return the session with its handoff recorded
   * @throws ConflictException {@code checkout.handoff-not-required} if the order now fits a tier,
   *     {@code checkout.step1-incomplete}, {@code checkout.basket-unpriced}
   */
  public Mono<CheckoutSession> handoff(String id, AuthenticatedCustomer customer, String locale) {
    final Locale language = LocaleParser.parse(locale);
    return sessions
        .requireVerified(customer)
        .then(sessions.find(id, customer))
        .flatMap(
            found ->
                found.handoff().isPresent()
                    ? Mono.just(found)
                    : evaluation
                        .evaluate(id, customer)
                        .flatMap(evaluated -> submit(evaluated, language)));
  }

  private Mono<CheckoutSession> submit(CheckoutSession session, Locale language) {
    return session.delivery().orElseThrow().stage() == CheckoutStage.HANDOFF_REQUIRED
        ? customers.resolve(session).flatMap(owner -> create(session, owner, language))
        : Mono.error(
            new ConflictException(
                "checkout.handoff-not-required",
                "This order can be priced automatically; continue to payment"));
  }

  private Mono<CheckoutSession> create(CheckoutSession session, CustomerId owner, Locale language) {
    final Instant now = clock.instant();
    return numbers
        .next(LocalDate.ofInstant(now, ZoneOffset.UTC))
        .flatMap(
            number -> {
              final Order order = handedOff(session, owner, number, now);
              final HandoffRecord record = new HandoffRecord(number.format(), order.id(), now);
              return store
                  .update(session.id(), current -> current.withHandoff(record))
                  .flatMap(claimed -> finish(claimed, record, order, language));
            });
  }

  private Mono<CheckoutSession> finish(
      CheckoutSession claimed, HandoffRecord ours, Order order, Locale language) {
    final boolean won = claimed.handoff().map(ours::equals).orElse(false);
    return won
        ? orders
            .save(orderMapper.toDocument(order))
            .then(mails.announce(claimed, ours.reference(), language))
            .thenReturn(claimed)
        : Mono.just(claimed);
  }

  private static Order handedOff(
      CheckoutSession session, CustomerId owner, OrderNumber number, Instant now) {
    final Order created =
        Order.created(
            new OrderId(UUID.randomUUID().toString()),
            number,
            owner,
            session.details().orElseThrow().deliveryAddress(),
            session.lines(),
            now);
    return OrderTransitions.advance(
        OrderTransitions.advance(created, TransitionTrigger.CHECKOUT_SUBMITTED, now),
        TransitionTrigger.TIER_EXCEEDED,
        now);
  }
}
