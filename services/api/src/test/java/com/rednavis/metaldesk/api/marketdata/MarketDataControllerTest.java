package com.rednavis.metaldesk.api.marketdata;

import com.rednavis.metaldesk.api.currency.DisplayCurrencies;
import com.rednavis.metaldesk.api.currency.FakeExchangeRates;
import com.rednavis.metaldesk.api.currency.FxProperties;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The panel endpoint reports direction and magnitude after two observations, and no change after
 * one.
 */
class MarketDataControllerTest {

  private static final String SIXTY = "60.00";
  private static final String FIRST_PRICE = "$[0].pricePerGram";
  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

  private final ReferencePriceCache cache = new ReferencePriceCache();
  private final WebTestClient client =
      WebTestClient.bindToController(
              new MarketDataController(new MarketDataService(cache), currencies()))
          .build();

  private static DisplayCurrencies currencies() {
    return new DisplayCurrencies(
        new FakeExchangeRates(
            new FxProperties(Currency.EUR, "FAKE", Map.of(Currency.USD, new BigDecimal("2.00")))));
  }

  private static ReferencePrice price(Metal metal, String amount, Instant at) {
    return new ReferencePrice(metal, Money.of(amount, Currency.EUR), at);
  }

  @Test
  void coldStartServesAnEmptyPanel() {
    client
        .get()
        .uri("/api/market-data/prices")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
  }

  @Test
  void singleObservationHasNoChangeFieldRatherThanZeroChange() {
    cache.record(price(Metal.GOLD, SIXTY, T0));

    client
        .get()
        .uri("/api/market-data/prices")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].metal")
        .isEqualTo("GOLD")
        .jsonPath(FIRST_PRICE)
        .isEqualTo(SIXTY)
        .jsonPath("$[0].currency")
        .isEqualTo("EUR")
        .jsonPath("$[0].change")
        .doesNotExist();
  }

  @Test
  void twoObservationsReportDirectionAndMagnitude() {
    cache.record(price(Metal.GOLD, SIXTY, T0));
    cache.record(price(Metal.GOLD, "63.00", T0.plusSeconds(20)));
    cache.record(price(Metal.SILVER, "0.80", T0));
    cache.record(price(Metal.SILVER, "0.72", T0.plusSeconds(20)));

    client
        .get()
        .uri("/api/market-data/prices")
        .exchange()
        .expectBody()
        .jsonPath("$[0].metal")
        .isEqualTo("GOLD")
        .jsonPath(FIRST_PRICE)
        .isEqualTo("63.00")
        .jsonPath("$[0].change.direction")
        .isEqualTo("UP")
        .jsonPath("$[0].change.amount")
        .isEqualTo("3.00")
        .jsonPath("$[0].change.percent")
        .isEqualTo("5.00")
        .jsonPath("$[1].metal")
        .isEqualTo("SILVER")
        .jsonPath("$[1].change.direction")
        .isEqualTo("DOWN")
        .jsonPath("$[1].change.amount")
        .isEqualTo("0.08")
        .jsonPath("$[1].change.percent")
        .isEqualTo("10.00");
  }

  @Test
  void pricesAndChangesAreConvertedForDisplayAndTheDirectionIsKept() {
    cache.record(price(Metal.GOLD, SIXTY, T0));
    cache.record(price(Metal.GOLD, "63.00", T0.plusSeconds(20)));

    client
        .get()
        .uri("/api/market-data/prices?currency=USD")
        .exchange()
        .expectBody()
        .jsonPath(FIRST_PRICE)
        .isEqualTo("126.00")
        .jsonPath("$[0].currency")
        .isEqualTo("USD")
        .jsonPath("$[0].change.direction")
        .isEqualTo("UP")
        .jsonPath("$[0].change.amount")
        .isEqualTo("6.00")
        .jsonPath("$[0].change.percent")
        .isEqualTo("5.00");
  }

  @Test
  void anAbsentChangeStaysAbsentWhenConverted() {
    cache.record(price(Metal.GOLD, SIXTY, T0));

    client
        .get()
        .uri("/api/market-data/prices?currency=USD")
        .exchange()
        .expectBody()
        .jsonPath(FIRST_PRICE)
        .isEqualTo("120.00")
        .jsonPath("$[0].change")
        .doesNotExist();
  }
}
