package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.FulfillmentTierDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/** Reactive access to stored fulfillment tiers. */
public interface FulfillmentTierRepository
    extends ReactiveMongoRepository<FulfillmentTierDocument, String> {

  /**
   * Lists the tiers configured for a region, backed by the index on the region.
   *
   * @param region the region code
   * @return the region's tiers, possibly several or none
   */
  Flux<FulfillmentTierDocument> findByRegion(String region);
}
