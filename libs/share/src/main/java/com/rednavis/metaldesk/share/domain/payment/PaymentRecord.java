package com.rednavis.metaldesk.share.domain.payment;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * The record of a payment against an order (Architecture section 3).
 *
 * <p><strong>Interim shape.</strong> T-014 needs an order to hold a payment record, so this type
 * exists with only the amount. T-016 owns it and adds the provider, method, status and provider
 * reference; it should replace this file. That shape must stay defensive: a provider reference and
 * never an instrument, and no generic map or free-form details field, because a data model that can
 * hold a card number will eventually hold one.
 *
 * @param amount the amount paid or to be paid, never null
 */
public record PaymentRecord(Money amount) {

  /**
   * Validates the amount.
   *
   * @throws ValidationException if the amount is null
   */
  public PaymentRecord {
    if (amount == null) {
      throw new ValidationException(
          "payment-record.amount-missing", "Payment amount must not be null");
    }
  }
}
