package com.rednavis.metaldesk.api.order;

import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * Who carries an order and how to follow it. It exists only once the order has shipped; before that
 * an order has no shipment, not an empty one.
 *
 * @param carrier the carrier's name
 * @param trackingReference the carrier's tracking reference
 */
public record ShipmentDetails(String carrier, String trackingReference) {

  /**
   * Validates the details.
   *
   * @throws ValidationException if either is blank
   */
  public ShipmentDetails {
    if (carrier == null
        || carrier.isBlank()
        || trackingReference == null
        || trackingReference.isBlank()) {
      throw new ValidationException(
          "shipment.field-blank", "A shipment needs a carrier and a tracking reference");
    }
    carrier = carrier.strip();
    trackingReference = trackingReference.strip();
  }
}
