package com.rednavis.metaldesk.api.order.dto;

/**
 * Carrier and tracking of a shipped order.
 *
 * @param carrier the carrier's name
 * @param trackingReference the carrier's tracking reference
 */
public record ShipmentView(String carrier, String trackingReference) {}
