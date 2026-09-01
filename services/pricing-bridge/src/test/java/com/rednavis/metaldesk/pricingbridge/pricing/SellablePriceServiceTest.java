package com.rednavis.metaldesk.pricingbridge.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.pricingbridge.subscription.ReferencePriceBook;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.ProductSpecification;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.measure.Purity;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.Margin;
import com.rednavis.metaldesk.share.domain.pricing.PriceDerivation;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The service is the shared BR-3 formula and nothing else. */
class SellablePriceServiceTest {

  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
  private static final ProductSpecification BAR =
      new ProductSpecification(
          Metal.GOLD, Purity.of("999.9"), Weight.of("31.1035", WeightUnit.GRAM), Optional.empty());
  private static final PriceRule RULE =
      new PriceRule(Margin.of("5"), new PriceRule.Scope.ForProduct(new ProductId("bar-1")));

  private final ReferencePriceBook book = new ReferencePriceBook(Clock.systemUTC());
  private final SellablePriceService service = new SellablePriceService(book);

  private static ReferencePriceTick tick(String price) {
    return new ReferencePriceTick(Metal.GOLD, Money.of(price, Currency.EUR), T0, "test");
  }

  @Test
  void derivingFromTickEqualsDirectPriceDerivationCall() {
    final ReferencePriceTick tick = tick("61.37");

    assertEquals(
        PriceDerivation.derive(tick.toReferencePrice(), BAR, RULE),
        service.derive(tick, BAR, RULE));
  }

  @Test
  void quotingFromLatestPriceEqualsDirectPriceDerivationCall() {
    final ReferencePriceTick tick = tick("61.37");
    book.record(tick);

    final SellablePrice quoted = service.quote(BAR, RULE).orElseThrow();

    assertEquals(PriceDerivation.derive(tick.toReferencePrice(), BAR, RULE), quoted);
  }

  @Test
  void quotesFollowTheLatestPrice() {
    book.record(tick("61.37"));
    final SellablePrice before = service.quote(BAR, RULE).orElseThrow();
    book.record(
        new ReferencePriceTick(
            Metal.GOLD, Money.of("70.00", Currency.EUR), T0.plusSeconds(1), "test"));

    assertTrue(
        service.quote(BAR, RULE).orElseThrow().unitPrice().compareTo(before.unitPrice()) > 0);
  }

  @Test
  void hasNoQuoteBeforePriceWasObserved() {
    assertTrue(service.quote(BAR, RULE).isEmpty());
  }
}
