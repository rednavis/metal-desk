package com.rednavis.metaldesk.share.domain.payment;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * The record of a payment against an order (Architecture section 3): which provider, by which
 * method, in what status, under what reference, for how much.
 *
 * <p><strong>It never stores raw payment instrument data</strong> (Architecture section 3, and the
 * platform's promise never to hold raw card data). That is made a property of the model rather than
 * a policy: the record holds a {@link ProviderReference}, an opaque token, charge id or invoice
 * number, and nothing shaped like a card number, a security code or a bank account number. It also
 * has no generic bag of extra fields, no free-form details and no raw provider response. A bag like
 * that is how instrument data arrives in practice ("the provider returns extra fields we might
 * need"), so it is refused by design: if a provider answer needs keeping, it gets a named, typed
 * field here, reviewed for exactly this reason. A test asserts the record's components.
 *
 * @param providerId which provider handled the payment, trimmed and never blank
 * @param method how the customer paid, never null
 * @param status where the payment stands, never null
 * @param reference the provider's opaque handle for the payment, never null
 * @param amount the amount, greater than zero
 */
public record PaymentRecord(
    String providerId,
    PaymentMethod method,
    PaymentStatus status,
    ProviderReference reference,
    Money amount) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the provider id is null or blank, a field is null, or the amount
   *     is not greater than zero
   */
  public PaymentRecord {
    if (providerId == null || providerId.isBlank()) {
      throw new ValidationException(
          "payment-record.provider-blank", "Payment provider id must not be null or blank");
    }
    providerId = providerId.strip();
    if (method == null || status == null || reference == null) {
      throw new ValidationException(
          "payment-record.field-missing", "Payment requires a method, status and reference");
    }
    if (amount == null || amount.isZero() || amount.isNegative()) {
      throw new ValidationException(
          "payment-record.amount-invalid", "Payment amount must be present and above zero");
    }
  }
}
