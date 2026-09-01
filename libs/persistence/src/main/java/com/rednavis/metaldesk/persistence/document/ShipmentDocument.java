package com.rednavis.metaldesk.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Who carries an order and how to follow it, entered by staff once it ships. It lives beside the
 * order, not in it, so saving an order through the domain can never overwrite it.
 *
 * @param orderId the order's id
 * @param carrier the carrier's name
 * @param trackingReference the carrier's tracking reference
 */
@Document("shipments")
public record ShipmentDocument(@Id String orderId, String carrier, String trackingReference) {}
