package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.CredentialDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/** Reactive access to stored credentials, looked up by customer id. */
public interface CredentialRepository extends ReactiveMongoRepository<CredentialDocument, String> {}
