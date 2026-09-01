package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.ShipmentDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/** Stores {@link ShipmentDocument}s. */
public interface ShipmentRepository extends ReactiveMongoRepository<ShipmentDocument, String> {}
