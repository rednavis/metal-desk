package com.rednavis.metaldesk.share.domain.fulfillment;

import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * The estimated time a delivery takes, as a range of business days (BRD FR-5.1).
 *
 * <p>The legacy catalog showed this as free text such as "1-3 Business Days". A range of whole days
 * is what a customer is actually told, and it lets tiers be compared: a tier that is never slower
 * than another and sometimes faster is better.
 *
 * @param minDays the fastest estimate in business days, at least 1
 * @param maxDays the slowest estimate in business days, at least {@code minDays}
 */
public record TransitTime(int minDays, int maxDays) {

  private static final int MIN_DAYS = 1;

  /**
   * Validates the range.
   *
   * @throws ValidationException if {@code minDays} is below 1 or {@code maxDays} is below {@code
   *     minDays}
   */
  public TransitTime {
    if (minDays < MIN_DAYS) {
      throw new ValidationException(
          "transit-time.min-not-positive",
          "Transit time must be at least " + MIN_DAYS + " day, was " + minDays);
    }
    if (maxDays < minDays) {
      throw new ValidationException(
          "transit-time.range-inverted",
          "Transit time maximum " + maxDays + " is below its minimum " + minDays);
    }
  }
}
