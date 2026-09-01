package com.rednavis.metaldesk.pricingbridge.web;

import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.pricingbridge.pricing.SellablePriceService;
import com.rednavis.metaldesk.pricingbridge.subscription.ReferencePriceBook;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The quote endpoint prices a described product and fails in the platform's error shape. */
class SellablePriceControllerTest {

  private static final String METAL = "metal";
  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
  private static final Map<String, Object> BAR =
      Map.of(
          "scope",
          Map.of("type", "PRODUCT", "id", "bar-1"),
          METAL,
          "GOLD",
          "purity",
          "999.9",
          "weight",
          "100",
          "weightUnit",
          "GRAM",
          "marginPercent",
          "5");

  private final ReferencePriceBook book = new ReferencePriceBook(Clock.systemUTC());
  private final WebTestClient client =
      WebTestClient.bindToController(new SellablePriceController(new SellablePriceService(book)))
          .controllerAdvice(new ApiExceptionHandler())
          .build();

  private WebTestClient.ResponseSpec post(Map<String, Object> body) {
    return client
        .post()
        .uri("/api/sellable-prices/quotes")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange();
  }

  @Test
  void pricesProductFromLatestReferencePrice() {
    book.record(new ReferencePriceTick(Metal.GOLD, Money.of("60.00", Currency.EUR), T0, "test"));

    // 100 g x 0.9999 fine x 60.00 x 1.05 = 6299.37
    post(BAR)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.unitPrice")
        .isEqualTo("6299.37")
        .jsonPath("$.currency")
        .isEqualTo("EUR")
        .jsonPath("$.referencePrice")
        .isEqualTo("60.00")
        .jsonPath("$.scope.type")
        .isEqualTo("PRODUCT");
  }

  @Test
  void isNotFoundBeforeAnyPriceWasObserved() {
    post(BAR)
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("reference-price.unavailable")
        .jsonPath("$.correlationId")
        .isNotEmpty();
  }

  @Test
  void refusesAnOutOfRangeMargin() {
    post(Map.of(
            "scope",
            Map.of("type", "CATEGORY", "id", "bars"),
            METAL,
            "GOLD",
            "purity",
            "999.9",
            "weight",
            "100",
            "weightUnit",
            "GRAM",
            "marginPercent",
            "150"))
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void refusesAnIncompleteRequest() {
    post(Map.of(METAL, "GOLD"))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("quote.incomplete");
  }

  @Test
  void refusesAnUnknownMetalWithTheErrorEnvelope() {
    post(Map.of("scope", Map.of("type", "PRODUCT", "id", "bar-1"), METAL, "COPPER"))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid");
  }
}
