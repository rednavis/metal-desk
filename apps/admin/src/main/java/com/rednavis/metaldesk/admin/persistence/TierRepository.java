package com.rednavis.metaldesk.admin.persistence;

import com.rednavis.metaldesk.persistence.document.FulfillmentTierDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/** The fulfillment tiers. */
public interface TierRepository extends MongoRepository<FulfillmentTierDocument, String> {

  /**
   * Finds the tiers of a region.
   *
   * @param region the region code
   * @return its tiers, in no particular order
   */
  List<FulfillmentTierDocument> findByRegion(String region);
}
