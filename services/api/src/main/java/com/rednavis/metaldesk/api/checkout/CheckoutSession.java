package com.rednavis.metaldesk.api.checkout;

import com.rednavis.metaldesk.api.cart.CartId;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryState;
import com.rednavis.metaldesk.api.checkout.delivery.HandoffRecord;
import com.rednavis.metaldesk.api.checkout.payment.PaymentPhase;
import com.rednavis.metaldesk.api.checkout.payment.PaymentState;
import com.rednavis.metaldesk.api.checkout.step1.ConsentRecord;
import com.rednavis.metaldesk.api.checkout.step1.ConversionState;
import com.rednavis.metaldesk.api.checkout.step1.CustomerDetails;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The server-side state of one checkout: the basket it started from and what has been collected so
 * far, the stage its delivery evaluation permits, and what is known about paying.
 *
 * <p>It is persisted and found by an opaque id; see the package description for why that is not a
 * contradiction of the stateless-authentication decision. It is immutable: each change returns a
 * new session.
 *
 * @param id the session's opaque reference
 * @param owner the signed-in customer who started it, empty for a guest
 * @param source where the basket came from
 * @param cart the cart it came from, empty for a buy-now
 * @param lines the basket lines as quoted when the session started; a preview, since a price is
 *     final only once an order snapshots it (BRD BR-2)
 * @param details the step-1 customer and delivery data, once submitted
 * @param consent the privacy acceptance, once given
 * @param conversion the guest's quick registration, if they asked for one
 * @param delivery the outcome of the last delivery evaluation, empty before one
 * @param handoff the manager handoff, empty unless the session was handed to staff
 * @param payment what is known about paying, empty until a method is chosen
 * @param lifecycle its version and times
 */
public record CheckoutSession(
    String id,
    Optional<CustomerId> owner,
    Source source,
    Optional<CartId> cart,
    List<OrderLine> lines,
    Optional<CustomerDetails> details,
    Optional<ConsentRecord> consent,
    Optional<ConversionState> conversion,
    Optional<DeliveryState> delivery,
    Optional<HandoffRecord> handoff,
    Optional<PaymentState> payment,
    Lifecycle lifecycle) {

  /** Where the basket of a session came from. */
  public enum Source {
    /** The persistent cart. */
    CART,
    /** A buy-now of one unit that skipped the cart. */
    BUY_NOW
  }

  /**
   * Validates the session and copies its lines.
   *
   * @throws ValidationException if a field is null or there are no lines
   */
  public CheckoutSession {
    if (Stream.of(
            id,
            owner,
            source,
            cart,
            lines,
            details,
            consent,
            conversion,
            delivery,
            handoff,
            payment,
            lifecycle)
        .anyMatch(Objects::isNull)) {
      throw new ValidationException(
          "checkout-session.field-missing", "A checkout session needs all of its fields");
    }
    if (lines.isEmpty()) {
      throw new ValidationException(
          "checkout-session.basket-empty", "A checkout session needs at least one line");
    }
    lines = List.copyOf(lines);
  }

  /**
   * Records step 1.
   *
   * @param newDetails the validated customer and delivery data
   * @param newConsent the privacy acceptance
   * @param newConversion the guest's quick registration, or empty
   * @return the session with step 1 done; going back and submitting again simply replaces it. The
   *     delivery evaluation is dropped, because it was made for the old address, and so is any
   *     order (the caller cancels it first).
   */
  public CheckoutSession withStep1(
      CustomerDetails newDetails,
      ConsentRecord newConsent,
      Optional<ConversionState> newConversion) {
    return new CheckoutSession(
        id,
        owner,
        source,
        cart,
        lines,
        Optional.of(newDetails),
        Optional.of(newConsent),
        newConversion,
        Optional.empty(),
        handoff,
        payment.map(PaymentState::withoutOrder),
        lifecycle);
  }

  /**
   * Records that the guest's email has been confirmed.
   *
   * @return the session with the conversion marked verified; this same session if there is none
   */
  public CheckoutSession withConversionVerified() {
    return conversion
        .map(
            state ->
                new CheckoutSession(
                    id,
                    owner,
                    source,
                    cart,
                    lines,
                    details,
                    consent,
                    Optional.of(
                        new ConversionState(
                            state.email(), state.reference(), state.customer(), true)),
                    delivery,
                    handoff,
                    payment,
                    lifecycle))
        .orElse(this);
  }

  /**
   * Records a delivery evaluation, together with the basket lines it was evaluated on.
   *
   * @param newLines the lines as priced at the evaluation
   * @param evaluation what the evaluation decided
   * @return the session with the evaluation recorded
   */
  public CheckoutSession withDelivery(List<OrderLine> newLines, DeliveryState evaluation) {
    return new CheckoutSession(
        id,
        owner,
        source,
        cart,
        newLines,
        details,
        consent,
        conversion,
        Optional.of(evaluation),
        handoff,
        payment,
        lifecycle);
  }

  /**
   * Records the manager handoff, once: a session already handed off keeps its first handoff.
   *
   * @param record the handoff
   * @return the session with the handoff recorded; this same session if it already has one
   */
  public CheckoutSession withHandoff(HandoffRecord record) {
    return handoff.isPresent()
        ? this
        : new CheckoutSession(
            id,
            owner,
            source,
            cart,
            lines,
            details,
            consent,
            conversion,
            delivery,
            Optional.of(record),
            payment,
            lifecycle);
  }

  /**
   * Gives the session new lifecycle values, as the store does on every write.
   *
   * @param next the lifecycle
   * @return the same session with it
   */
  public CheckoutSession withLifecycle(Lifecycle next) {
    return new CheckoutSession(
        id,
        owner,
        source,
        cart,
        lines,
        details,
        consent,
        conversion,
        delivery,
        handoff,
        payment,
        next);
  }

  /**
   * Records what is known about paying.
   *
   * @param state the payment state
   * @return the session with it
   */
  public CheckoutSession withPayment(PaymentState state) {
    return new CheckoutSession(
        id,
        owner,
        source,
        cart,
        lines,
        details,
        consent,
        conversion,
        delivery,
        handoff,
        Optional.of(state),
        lifecycle);
  }

  /**
   * Adopts the terms staff set on the handed-off order: the delivery state becomes the quoted one
   * and the payment state is bound to that order, so it is the order that gets paid.
   *
   * @param quoted the delivery state carrying the staff-decided quote
   * @param handedOff the handed-off order
   * @return the session ready for payment; the chosen method, if any, is kept
   */
  public CheckoutSession withQuotedHandoff(DeliveryState quoted, OrderId handedOff) {
    return new CheckoutSession(
        id,
        owner,
        source,
        cart,
        lines,
        details,
        consent,
        conversion,
        Optional.of(quoted),
        handoff,
        Optional.of(
            payment
                .map(state -> state.withOrder(handedOff))
                .orElseGet(
                    () ->
                        new PaymentState(
                            Optional.empty(),
                            Optional.of(handedOff),
                            Optional.empty(),
                            PaymentPhase.ORDER_CREATED,
                            Locale.ENGLISH))),
        lifecycle);
  }

  /**
   * Forgets the delivery evaluation and any order, because an earlier step was edited.
   *
   * @return the session that must be evaluated and paid for afresh; the chosen method is kept
   */
  public CheckoutSession withoutEvaluation() {
    return new CheckoutSession(
        id,
        owner,
        source,
        cart,
        lines,
        details,
        consent,
        conversion,
        Optional.empty(),
        handoff,
        payment.map(PaymentState::withoutOrder),
        lifecycle);
  }
}
