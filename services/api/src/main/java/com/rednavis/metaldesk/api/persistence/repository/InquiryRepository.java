package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.InquiryDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/** Stores {@link InquiryDocument}s. */
public interface InquiryRepository extends ReactiveMongoRepository<InquiryDocument, String> {

  /**
   * Finds an inquiry by the reference the customer quotes.
   *
   * @param reference the reference
   * @return the inquiry, or empty
   */
  Mono<InquiryDocument> findByReference(String reference);
}
