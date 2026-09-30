package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.api.persistence.document.InvoiceArchiveDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/** Stores {@link InvoiceArchiveDocument}s. */
public interface InvoiceArchiveRepository
    extends ReactiveMongoRepository<InvoiceArchiveDocument, String> {}
