package com.rednavis.metaldesk.api.persistence.mapper;

import com.rednavis.metaldesk.api.persistence.document.FulfillmentTierDocument;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;
import com.rednavis.metaldesk.share.domain.fulfillment.TransitTime;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import org.springframework.stereotype.Component;

/** Maps a {@link FulfillmentTier} to its {@link FulfillmentTierDocument} and back. */
@Component
public class FulfillmentTierMapper {

  /**
   * Rebuilds the domain tier.
   *
   * @param document the stored tier
   * @return the tier
   */
  public FulfillmentTier toDomain(FulfillmentTierDocument document) {
    return new FulfillmentTier(
        new FulfillmentTierId(document.id()),
        new Region(document.region()),
        ValueMapper.moneyToDomain(document.valueCeiling()),
        ValueMapper.weightToDomain(document.weightCeiling()),
        ValueMapper.moneyToDomain(document.deliveryPrice()),
        new TransitTime(document.minDays(), document.maxDays()));
  }

  /**
   * Builds the document to store.
   *
   * @param tier the tier
   * @return the document
   */
  public FulfillmentTierDocument toDocument(FulfillmentTier tier) {
    return new FulfillmentTierDocument(
        tier.id().value(),
        tier.region().code(),
        ValueMapper.moneyToDocument(tier.valueCeiling()),
        ValueMapper.weightToDocument(tier.weightCeiling()),
        ValueMapper.moneyToDocument(tier.deliveryPrice()),
        tier.transit().minDays(),
        tier.transit().maxDays());
  }
}
