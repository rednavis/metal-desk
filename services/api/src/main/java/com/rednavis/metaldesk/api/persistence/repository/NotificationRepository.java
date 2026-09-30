package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.NotificationDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/** Stores {@link NotificationDocument}s. */
public interface NotificationRepository
    extends ReactiveMongoRepository<NotificationDocument, String> {}
