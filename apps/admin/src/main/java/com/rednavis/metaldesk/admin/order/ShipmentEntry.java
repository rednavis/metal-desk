package com.rednavis.metaldesk.admin.order;

import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * What staff enter when an order ships: who carries it and how to follow it (BRD FR-10.1).
 *
 * <p>Both are free text from a person, so they are trimmed, bounded and refused if they hold
 * control characters; nothing else about a carrier's format can be known here.
 *
 * @param carrier the carrier's name, not blank, at most 100 characters
 * @param trackingReference the tracking reference, not blank, at most 100 characters
 */
public record ShipmentEntry(String carrier, String trackingReference) {

  private static final int MAX_LENGTH = 100;

  /**
   * Validates and trims the fields.
   *
   * @throws ValidationException if either is missing, blank, too long or has control characters
   */
  public ShipmentEntry {
    carrier = clean(carrier, "shipment.carrier-invalid", "Carrier");
    trackingReference = clean(trackingReference, "shipment.tracking-invalid", "Tracking reference");
  }

  private static String clean(String value, String code, String label) {
    if (value == null
        || value.isBlank()
        || value.strip().length() > MAX_LENGTH
        || value.chars().anyMatch(Character::isISOControl)) {
      throw new ValidationException(
          code,
          label + " is required, at most " + MAX_LENGTH + " characters, no control characters");
    }
    return value.strip();
  }
}
