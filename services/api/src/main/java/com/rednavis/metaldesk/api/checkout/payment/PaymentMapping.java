package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.persistence.document.SessionPaymentDocument;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import java.util.Optional;

/** Maps a session's payment state to its stored form and back. */
public final class PaymentMapping {

  private PaymentMapping() {}

  /**
   * Rebuilds a payment state.
   *
   * @param document the stored state
   * @return the state
   */
  public static PaymentState stateOf(SessionPaymentDocument document) {
    return new PaymentState(
        Optional.ofNullable(document.method()).map(PaymentMethod::valueOf),
        Optional.ofNullable(document.orderId()).map(OrderId::new),
        Optional.ofNullable(document.reference()).map(ProviderReference::new),
        PaymentPhase.valueOf(document.phase()));
  }

  /**
   * Builds the stored form of a payment state.
   *
   * @param state the state
   * @return the document
   */
  public static SessionPaymentDocument stateDocument(PaymentState state) {
    return new SessionPaymentDocument(
        state.method().map(PaymentMethod::name).orElse(null),
        state.order().map(OrderId::value).orElse(null),
        state.reference().map(ProviderReference::value).orElse(null),
        state.phase().name());
  }
}
