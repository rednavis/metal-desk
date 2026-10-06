package com.rednavis.metaldesk.share.domain.fulfillment;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;

/**
 * The price and terms staff set for an order that went to manager handoff (BRD FR-5.3: "final price
 * and terms are set by a human").
 *
 * <p>The final price is one figure: what staff have agreed the customer will pay for the order. How
 * it divides into goods, tax and delivery on the invoice is the admin flow's business (T-040), not
 * decided here. The terms are staff-written text for the customer, bounded so the field stays a set
 * of terms and not a place to keep anything else.
 *
 * @param finalPrice the price staff set, greater than zero
 * @param terms the terms staff set, trimmed, not blank and at most {@value #MAX_TERMS_LENGTH}
 *     characters
 * @param quotedAt when staff set the quote, never null
 */
public record ManagerQuote(Money finalPrice, String terms, Instant quotedAt) {

  /** The longest allowed terms text, in characters. */
  public static final int MAX_TERMS_LENGTH = 2000;

  /**
   * Validates the fields.
   *
   * @throws ValidationException if a field is null, the price is not greater than zero, or the
   *     terms are blank or too long
   */
  public ManagerQuote {
    if (finalPrice == null || finalPrice.isZero() || finalPrice.isNegative()) {
      throw new ValidationException(
          "manager-quote.price-invalid", "Manager quote price must be present and above zero");
    }
    if (terms == null || terms.isBlank()) {
      throw new ValidationException(
          "manager-quote.terms-blank", "Manager quote terms must not be null or blank");
    }
    terms = terms.strip();
    if (terms.length() > MAX_TERMS_LENGTH) {
      throw new ValidationException(
          "manager-quote.terms-too-long",
          "Manager quote terms must be at most " + MAX_TERMS_LENGTH + " characters");
    }
    if (quotedAt == null) {
      throw new ValidationException(
          "manager-quote.instant-missing", "Manager quote time must not be null");
    }
  }
}
