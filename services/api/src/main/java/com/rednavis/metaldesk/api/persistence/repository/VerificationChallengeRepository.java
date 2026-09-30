package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.VerificationChallengeDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/** Reactive access to verification challenges, by reference. */
public interface VerificationChallengeRepository
    extends ReactiveMongoRepository<VerificationChallengeDocument, String> {

  /**
   * Lists the challenges bound to a subject for a purpose.
   *
   * @param purpose the purpose name
   * @param subject the customer id
   * @return the challenges, in any state
   */
  Flux<VerificationChallengeDocument> findByPurposeAndSubject(String purpose, String subject);
}
