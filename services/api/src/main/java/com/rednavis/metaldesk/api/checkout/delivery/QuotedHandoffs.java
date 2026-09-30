package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.api.checkout.payment.PaymentState;
import com.rednavis.metaldesk.api.persistence.repository.ManagerQuoteRepository;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.persistence.document.ManagerQuoteDocument;
import com.rednavis.metaldesk.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Lets a customer pay once staff have set the terms of a handed-off order (BRD FR-5.3).
 *
 * <p>A handed-off session is refused payment because a human has to price it. When staff answer
 * (the order returns to {@code AWAITING_PAYMENT} carrying their delivery quote) the session is
 * <em>adopted</em>: its delivery state becomes {@code PAYMENT_ALLOWED} with that quote, and its
 * payment state is bound to the handed-off order, so the ordinary payment path charges that very
 * order, at the total it carries, instead of creating a second one. Nothing is computed here: the
 * price is the one staff decided.
 *
 * <p>Terms have a validity. Past it the session is refused with {@code checkout.quote-expired}
 * rather than charged on terms staff no longer stand behind, and this is checked on every call, not
 * only at adoption.
 */
@Component
@RequiredArgsConstructor
public class QuotedHandoffs {

  /** The code of a payment attempt on terms that have lapsed. */
  public static final String EXPIRED = "checkout.quote-expired";

  private final OrderRepository orders;
  private final OrderMapper mapper;
  private final ManagerQuoteRepository quotes;
  private final CheckoutSessionStore store;
  private final Clock clock;

  /**
   * Adopts the staff-set terms into the session, if there are any.
   *
   * @param session the session
   * @return the session as it now stands: the same one if it was never handed off or staff have not
   *     answered yet
   * @throws ConflictException {@code checkout.quote-expired} if the terms have lapsed
   */
  public Mono<CheckoutSession> adopt(CheckoutSession session) {
    return session
        .handoff()
        .map(
            record ->
                orders
                    .findById(record.orderId().value())
                    .map(mapper::toDomain)
                    .filter(QuotedHandoffs::quoted)
                    .flatMap(order -> bind(session, order))
                    .defaultIfEmpty(session))
        .orElseGet(() -> Mono.just(session));
  }

  private static boolean quoted(Order order) {
    return order.status() == OrderStatus.AWAITING_PAYMENT && order.quote().isPresent();
  }

  private Mono<CheckoutSession> bind(CheckoutSession session, Order order) {
    return quotes
        .findById(order.id().value())
        .map(ManagerQuoteDocument::validUntil)
        .filter(validUntil -> !validUntil.isAfter(clock.instant()))
        .flatMap(
            validUntil ->
                Mono.<CheckoutSession>error(
                    new ConflictException(
                        EXPIRED, "The terms for this order have expired; please contact us")))
        .switchIfEmpty(Mono.defer(() -> adopted(session, order)));
  }

  private Mono<CheckoutSession> adopted(CheckoutSession session, Order order) {
    final DeliveryState before = session.delivery().orElseThrow();
    final boolean already =
        before.stage() == CheckoutStage.PAYMENT_ALLOWED
            && session.payment().flatMap(PaymentState::order).isPresent();
    return already
        ? Mono.just(session)
        : store.update(
            session.id(),
            current ->
                current.withQuotedHandoff(
                    new DeliveryState(
                        CheckoutStage.PAYMENT_ALLOWED,
                        order.quote(),
                        Optional.empty(),
                        before.exTaxValue(),
                        before.weight(),
                        order.quote().orElseThrow().quotedAt()),
                    order.id()));
  }
}
