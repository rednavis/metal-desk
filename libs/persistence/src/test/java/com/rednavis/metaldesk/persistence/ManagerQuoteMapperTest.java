package com.rednavis.metaldesk.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rednavis.metaldesk.persistence.document.ManagerQuoteDocument;
import com.rednavis.metaldesk.persistence.fixtures.CatalogFixtures;
import com.rednavis.metaldesk.persistence.mapper.ManagerQuoteMapper;
import com.rednavis.metaldesk.share.domain.fulfillment.ManagerQuote;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The staff-set terms survive a round trip through their document. */
class ManagerQuoteMapperTest {

  private static final Instant QUOTED = Instant.parse("2026-10-01T10:00:00.123Z");
  private static final Instant VALID_UNTIL = Instant.parse("2026-10-31T00:00:00Z");

  private final ManagerQuoteMapper mapper = new ManagerQuoteMapper();

  @Test
  void roundTripsTheQuoteAndKeepsTheContext() {
    final ManagerQuote quote =
        new ManagerQuote(CatalogFixtures.eur("250.00"), "Insured courier", QUOTED);

    final ManagerQuoteDocument document =
        mapper.toDocument(new OrderId("o-1"), quote, VALID_UNTIL, "staff@example.com");

    assertEquals("o-1", document.orderId());
    assertEquals(VALID_UNTIL, document.validUntil());
    assertEquals("staff@example.com", document.staff());
    assertEquals(quote, mapper.toDomain(document));
  }
}
