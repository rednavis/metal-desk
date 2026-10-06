package com.rednavis.metaldesk.admin.persistence;

import com.rednavis.metaldesk.persistence.document.ProductDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/** The products; staff only read them, to work out an order's weight. */
public interface ProductRepository extends MongoRepository<ProductDocument, String> {}
