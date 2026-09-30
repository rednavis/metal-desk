package com.rednavis.metaldesk.persistence.mapper;

import com.rednavis.metaldesk.persistence.document.ManagerQuoteDocument;
import com.rednavis.metaldesk.share.domain.fulfillment.ManagerQuote;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Converts a {@link ManagerQuote} and the context it was set in to and from its document. */
@Component
public class ManagerQuoteMapper {

  /**
   * Builds the document to store.
   *
   * @param orderId the order the terms belong to
   * @param quote what staff decided
   * @param validUntil until when the customer may pay on it
   * @param staff who set it
   * @return the document
   */
  public ManagerQuoteDocument toDocument(
      OrderId orderId, ManagerQuote quote, Instant validUntil, String staff) {
    return new ManagerQuoteDocument(
        orderId.value(),
        ValueMapper.moneyToDocument(quote.finalPrice()),
        quote.terms(),
        quote.quotedAt(),
        validUntil,
        staff);
  }

  /**
   * Rebuilds the domain value.
   *
   * @param document the stored terms
   * @return what staff decided
   */
  public ManagerQuote toDomain(ManagerQuoteDocument document) {
    return new ManagerQuote(
        ValueMapper.moneyToDomain(document.finalPrice()), document.terms(), document.quotedAt());
  }
}
