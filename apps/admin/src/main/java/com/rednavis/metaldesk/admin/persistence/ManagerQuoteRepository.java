package com.rednavis.metaldesk.admin.persistence;

import com.rednavis.metaldesk.persistence.document.ManagerQuoteDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/** The terms staff set on handed-off orders, keyed by order id. */
public interface ManagerQuoteRepository extends MongoRepository<ManagerQuoteDocument, String> {}
