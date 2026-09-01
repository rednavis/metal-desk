package com.rednavis.metaldesk.share.domain.id;

/**
 * Identifies a fulfillment tier (BRD FR-5.1). Distinct from every other identifier type, so the
 * compiler rejects swapped arguments.
 *
 * @param value the identifier value, never null or blank
 */
public record FulfillmentTierId(String value) implements EntityId {

  /**
   * Validates the value.
   *
   * @throws com.rednavis.metaldesk.share.error.ValidationException if the value is null or blank
   */
  public FulfillmentTierId {
    EntityId.requireValue(value, "FulfillmentTierId");
  }
}
