package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentResultView;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentRecord;
import com.rednavis.metaldesk.share.domain.payment.PaymentStatus;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;

/** Builds the payment records and result views of a provider outcome. */
public final class PaymentResults {

  private PaymentResults() {}

  /**
   * Builds the record of a payment attempt for an order, at the order's own grand total.
   *
   * @param order the order
   * @param providerId the provider
   * @param method the method
   * @param status where the payment stands
   * @param reference the provider's reference
   * @return the record
   */
  public static PaymentRecord record(
      Order order,
      String providerId,
      PaymentMethod method,
      PaymentStatus status,
      ProviderReference reference) {
    return new PaymentRecord(providerId, method, status, reference, order.totals().grandTotal());
  }

  /**
   * A result that needs no error fields.
   *
   * @param order the order
   * @param result the outcome kind
   * @param redirectUrl where to send the customer, or null
   * @param clientHandle the payment element's client secret, or null
   * @param invoiceReference the invoice number, or null
   * @return the view
   */
  public static PaymentResultView of(
      Order order,
      String result,
      String redirectUrl,
      String clientHandle,
      String invoiceReference) {
    return new PaymentResultView(
        result,
        order.number().format(),
        redirectUrl,
        clientHandle,
        invoiceReference,
        null,
        null,
        null);
  }

  /**
   * A declined payment.
   *
   * @param order the order
   * @param reason the decline reason name
   * @param message what to tell the customer
   * @return the view
   */
  public static PaymentResultView declined(Order order, String reason, String message) {
    return new PaymentResultView(
        "DECLINED", order.number().format(), null, null, null, reason, null, message);
  }

  /**
   * An error that was not a decline.
   *
   * @param order the order
   * @param code the error code
   * @param message an actionable message
   * @return the view
   */
  public static PaymentResultView error(Order order, String code, String message) {
    return new PaymentResultView(
        "ERROR", order.number().format(), null, null, null, null, code, message);
  }

  /**
   * The error code of a provider that could not be used.
   *
   * @param failure what went wrong
   * @return {@code payment.provider-error} for an answer that could not be understood, else {@code
   *     payment.provider-unavailable}
   */
  public static String code(PaymentProviderException failure) {
    return failure.kind() == PaymentProviderException.Kind.MALFORMED_RESPONSE
        ? "payment.provider-error"
        : "payment.provider-unavailable";
  }

  /**
   * The message for a provider that could not be used. A timeout does not prove the payment failed,
   * so it warns the customer to check before trying again.
   *
   * @param order the order
   * @param failure what went wrong
   * @return the message
   */
  public static String message(Order order, PaymentProviderException failure) {
    return failure.kind() == PaymentProviderException.Kind.TIMEOUT
        ? "We could not confirm your payment in time. Please check your bank before trying again,"
            + " or contact us quoting order "
            + order.number().format()
        : "We could not reach the payment provider. Please try again in a moment.";
  }
}
