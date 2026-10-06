package com.rednavis.metaldesk.admin.persistence;

import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/** The customers; staff only read them, to address mail and show who an order belongs to. */
public interface CustomerRepository extends MongoRepository<CustomerDocument, String> {}
