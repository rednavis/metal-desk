package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.PriceRuleDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/** Reactive access to the configured price rules, looked up by their scope-derived id. */
public interface PriceRuleRepository extends ReactiveMongoRepository<PriceRuleDocument, String> {}
