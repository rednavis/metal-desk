package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Locale;
import java.util.Optional;

/**
 * What a checkout session knows about paying: the method chosen, the order created for it, and the
 * provider reference of a payment that is awaiting the customer.
 *
 * <p>A decline or failure changes nothing here except clearing the reference: the method and the
 * order stay, which is what lets the customer retry with nothing lost (BRD FR-6.3).
 *
 * @param method the chosen payment method, if one is chosen
 * @param order the order created for this checkout, if payment was started
 * @param reference the provider's reference of a payment awaiting confirmation
 * @param phase where payment stands
 * @param locale the customer's language, for the mails confirmation sends
 */
public record PaymentState(
    Optional<PaymentMethod> method,
    Optional<OrderId> order,
    Optional<ProviderReference> reference,
    PaymentPhase phase,
    Locale locale) {

  /**
   * Validates the state.
   *
   * @throws ValidationException if a field is null
   */
  public PaymentState {
    if (method == null || order == null || reference == null || phase == null || locale == null) {
      throw new ValidationException(
          "payment-state.field-missing", "A payment state needs every field");
    }
  }

  /**
   * A state with a method chosen and nothing else.
   *
   * @param chosen the method
   * @return the state
   */
  public static PaymentState selected(PaymentMethod chosen) {
    return new PaymentState(
        Optional.of(chosen),
        Optional.empty(),
        Optional.empty(),
        PaymentPhase.METHOD_SELECTED,
        Locale.ENGLISH);
  }

  /**
   * Chooses a method, keeping the order if there is one.
   *
   * @param chosen the method
   * @return the state with the new method
   */
  public PaymentState withMethod(PaymentMethod chosen) {
    return new PaymentState(Optional.of(chosen), order, reference, phase, locale);
  }

  /**
   * Moves to another phase.
   *
   * @param next the phase
   * @param pending the provider reference now awaiting confirmation, or empty
   * @return the state in that phase
   */
  public PaymentState in(PaymentPhase next, Optional<ProviderReference> pending) {
    return new PaymentState(method, order, pending, next, locale);
  }

  /**
   * Records the order created for this checkout.
   *
   * @param created the order's id
   * @return the state with the order
   */
  public PaymentState withOrder(OrderId created) {
    return new PaymentState(
        method, Optional.of(created), reference, PaymentPhase.ORDER_CREATED, locale);
  }

  /**
   * Forgets the order, as when the customer edits an earlier step and the order is cancelled.
   *
   * @return the state with the method kept and no order
   */
  public PaymentState withoutOrder() {
    return new PaymentState(
        method, Optional.empty(), Optional.empty(), PaymentPhase.METHOD_SELECTED, locale);
  }

  /**
   * Records the customer's language.
   *
   * @param language the language of the customer's mails
   * @return the state with that language
   */
  public PaymentState withLocale(Locale language) {
    return new PaymentState(method, order, reference, phase, language);
  }
}
