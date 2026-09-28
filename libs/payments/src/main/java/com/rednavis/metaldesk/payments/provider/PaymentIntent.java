package com.rednavis.metaldesk.payments.provider;

import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;

/**
 * What checkout asks a provider for: take this amount, for this order, by this method.
 *
 * <p><strong>It carries no payment instrument.</strong> Whatever the customer enters is entered
 * with the provider, in the provider's redirect or embedded element, and never passes through the
 * platform (Architecture section 3). So this record holds references and an amount and nothing
 * else: no bag of extra fields, no free-form details. It is the same rule as {@code PaymentRecord}
 * and for the same reason, and a test asserts it.
 *
 * <p>The return and cancel targets are {@link URI}s, not strings, so call sites cannot build them
 * by concatenation. Each must be absolute with an {@code http} or {@code https} scheme.
 *
 * @param orderId the order being paid, never null
 * @param amount the amount to take, greater than zero
 * @param method how the customer chose to pay, never null
 * @param customerId the paying customer, never null
 * @param returnUri where the provider sends the customer after completing, absolute {@code http} or
 *     {@code https}
 * @param cancelUri where the provider sends the customer if they back out, absolute {@code http} or
 *     {@code https}
 */
public record PaymentIntent(
    OrderId orderId,
    Money amount,
    PaymentMethod method,
    CustomerId customerId,
    URI returnUri,
    URI cancelUri) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if a field is null, the amount is not greater than zero, or a URI
   *     is not absolute {@code http} or {@code https}
   */
  public PaymentIntent {
    if (orderId == null || method == null || customerId == null) {
      throw new ValidationException(
          "payment-intent.field-missing", "Payment intent requires an order, method and customer");
    }
    if (amount == null || amount.isZero() || amount.isNegative()) {
      throw new ValidationException(
          "payment-intent.amount-invalid", "Payment amount must be present and above zero");
    }
    Checks.webUri(
        returnUri,
        "payment-intent.uri-invalid",
        "Payment return target must be an absolute http or https URI");
    Checks.webUri(
        cancelUri,
        "payment-intent.uri-invalid",
        "Payment cancel target must be an absolute http or https URI");
  }
}
