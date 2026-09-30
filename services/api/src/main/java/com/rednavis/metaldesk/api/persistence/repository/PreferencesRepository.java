package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.PreferencesDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/** Stores {@link PreferencesDocument}s, keyed by customer id. */
public interface PreferencesRepository
    extends ReactiveMongoRepository<PreferencesDocument, String> {}
