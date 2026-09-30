package com.rednavis.metaldesk.api.checkout.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The outcome of a payment, surfaced into the checkout session (BRD FR-7.2). It is one shape for
 * every outcome; {@code result} says which fields are present.
 *
 * <ul>
 *   <li>{@code CAPTURED}: paid.
 *   <li>{@code REDIRECT}: send the customer to {@code redirectUrl}; the outcome arrives at the
 *       callback.
 *   <li>{@code ELEMENT}: hand {@code clientHandle} to the embedded payment element.
 *   <li>{@code DOCUMENT_ISSUED}: an invoice was issued under {@code invoiceReference}; the order
 *       awaits payment against it.
 *   <li>{@code DECLINED}: the provider said no, with {@code declineReason}; the customer returns to
 *       method selection with everything intact.
 *   <li>{@code ERROR}: something went wrong that was not a decline, with an actionable {@code
 *       errorCode} and {@code message}; everything is intact and the customer may try again.
 * </ul>
 *
 * @param result the outcome kind
 * @param orderReference the order number
 * @param redirectUrl where to send the customer, for {@code REDIRECT}
 * @param clientHandle the client secret for the payment element, for {@code ELEMENT}
 * @param invoiceReference the invoice number, for {@code DOCUMENT_ISSUED}
 * @param declineReason why the payment was declined, for {@code DECLINED}
 * @param errorCode the error code, for {@code ERROR}
 * @param message a message the customer can act on, for {@code DECLINED} and {@code ERROR}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResultView(
    String result,
    String orderReference,
    String redirectUrl,
    String clientHandle,
    String invoiceReference,
    String declineReason,
    String errorCode,
    String message) {

  /** Omits the client handle, so a logged result does not leak it. */
  @Override
  public String toString() {
    return "PaymentResultView[result=" + result + ", orderReference=" + orderReference + ']';
  }
}
