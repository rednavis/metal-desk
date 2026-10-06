package com.rednavis.metaldesk.admin.persistence;

import com.rednavis.metaldesk.persistence.document.ShipmentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/** The shipment of each order, keyed by order id. */
public interface ShipmentRepository extends MongoRepository<ShipmentDocument, String> {}
