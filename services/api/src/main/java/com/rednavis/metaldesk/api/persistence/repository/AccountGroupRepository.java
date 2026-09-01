package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.AccountGroupDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/** Reactive access to the groups of accounts a person may switch between. */
public interface AccountGroupRepository
    extends ReactiveMongoRepository<AccountGroupDocument, String> {

  /**
   * Finds the group a customer belongs to, backed by the index on the members.
   *
   * @param customerId the customer id
   * @return the group, or empty if the customer is in none
   */
  Mono<AccountGroupDocument> findFirstByMembersContaining(String customerId);
}
