package com.rednavis.metaldesk.pricingbridge.web;

import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.pricingbridge.subscription.ReferencePriceBook;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The endpoint gives the latest and previous observation, and no change after just one. */
class ReferencePriceControllerTest {

  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

  private final ReferencePriceBook book = new ReferencePriceBook(Clock.systemUTC());
  private final WebTestClient client =
      WebTestClient.bindToController(new ReferencePriceController(book)).build();

  private void observe(Metal metal, String price, long seconds) {
    book.record(
        new ReferencePriceTick(metal, Money.of(price, Currency.EUR), T0.plusSeconds(seconds), "t"));
  }

  @Test
  void coldStartIsAnEmptyList() {
    client
        .get()
        .uri("/api/reference-prices")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
  }

  @Test
  void singleObservationHasNoChangeField() {
    observe(Metal.GOLD, "60.00", 0);

    client
        .get()
        .uri("/api/reference-prices")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].metal")
        .isEqualTo("GOLD")
        .jsonPath("$[0].pricePerGram")
        .isEqualTo("60.00")
        .jsonPath("$[0].currency")
        .isEqualTo("EUR")
        .jsonPath("$[0].change")
        .doesNotExist();
  }

  @Test
  void twoObservationsGiveDirectionAndMagnitude() {
    observe(Metal.GOLD, "60.00", 0);
    observe(Metal.GOLD, "63.00", 1);
    observe(Metal.SILVER, "1.00", 0);
    observe(Metal.SILVER, "0.90", 1);

    client
        .get()
        .uri("/api/reference-prices")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].pricePerGram")
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
        .jsonPath("$[1].change.percent")
        .isEqualTo("10.00");
  }
}
