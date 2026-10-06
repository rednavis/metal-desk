package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.ManagerQuoteDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/** Reads the terms staff set on a handed-off order, keyed by order id. */
public interface ManagerQuoteRepository
    extends ReactiveMongoRepository<ManagerQuoteDocument, String> {}
