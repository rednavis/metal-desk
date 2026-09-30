package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.persistence.document.DeliveryDocument;
import com.rednavis.metaldesk.api.persistence.document.HandoffDocument;
import com.rednavis.metaldesk.api.persistence.document.QuoteDocument;
import com.rednavis.metaldesk.api.persistence.mapper.ValueMapper;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.fulfillment.TransitTime;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import java.util.Optional;

/** Maps a session's delivery state and handoff to their stored forms and back. */
public final class DeliveryMapping {

  private DeliveryMapping() {}

  /** Rebuilds a delivery state. */
  public static DeliveryState deliveryOf(DeliveryDocument document) {
    return new DeliveryState(
        CheckoutStage.valueOf(document.stage()),
        Optional.ofNullable(document.quote()).map(DeliveryMapping::quoteOf),
        Optional.ofNullable(document.reason()).map(HandoffReason::valueOf),
        ValueMapper.moneyToDomain(document.exTaxValue()),
        ValueMapper.weightToDomain(document.weight()),
        document.evaluatedAt());
  }

  /** Builds the stored form of a delivery state. */
  public static DeliveryDocument deliveryDocument(DeliveryState state) {
    return new DeliveryDocument(
        state.stage().name(),
        state.quote().map(DeliveryMapping::quoteDocument).orElse(null),
        state.reason().map(HandoffReason::name).orElse(null),
        ValueMapper.moneyToDocument(state.exTaxValue()),
        ValueMapper.weightToDocument(state.weight()),
        state.evaluatedAt());
  }

  /** Rebuilds a delivery quote. */
  public static DeliveryQuote quoteOf(QuoteDocument document) {
    return new DeliveryQuote(
        new FulfillmentTierId(document.tierId()),
        ValueMapper.moneyToDomain(document.cost()),
        new TransitTime(document.minDays(), document.maxDays()),
        document.quotedAt());
  }

  /** Builds the stored form of a delivery quote. */
  public static QuoteDocument quoteDocument(DeliveryQuote quote) {
    return new QuoteDocument(
        quote.tierId().value(),
        ValueMapper.moneyToDocument(quote.cost()),
        quote.transit().minDays(),
        quote.transit().maxDays(),
        quote.quotedAt());
  }

  /** Rebuilds a handoff record. */
  public static HandoffRecord handoffOf(HandoffDocument document) {
    return new HandoffRecord(document.reference(), new OrderId(document.orderId()), document.at());
  }

  /** Builds the stored form of a handoff record. */
  public static HandoffDocument handoffDocument(HandoffRecord record) {
    return new HandoffDocument(record.reference(), record.orderId().value(), record.at());
  }
}
