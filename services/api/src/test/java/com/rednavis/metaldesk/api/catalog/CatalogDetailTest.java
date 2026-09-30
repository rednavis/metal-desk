package com.rednavis.metaldesk.api.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rednavis.metaldesk.api.marketdata.ReferencePriceCache;
import com.rednavis.metaldesk.api.persistence.MongoTestSupport;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.pricing.PriceDerivation;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Product detail, the derived price and the error cases, over HTTP against real MongoDB. */
@AutoConfigureWebTestClient
@Import(CatalogSeed.class)
class CatalogDetailTest extends MongoTestSupport {

  private static final String CODE = "$.code";

  @Autowired private WebTestClient client;
  @Autowired private CatalogSeed seed;
  @Autowired private ReferencePriceCache cache;

  private Product priced;
  private PriceRule rule;

  @BeforeEach
  void seedCatalog() {
    final Category bars = seed.category("d-bars", TaxCategory.INVESTMENT_GRADE);
    rule = seed.marginForCategory(bars, "5");
    priced = seed.pricedProduct("d-priced", "Detail gold bar", bars);
    seed.unpricedProduct("d-onreq", "Detail platinum coin", bars);
    seed.pricedProduct(
        "d-standard", "Detail standard item", seed.category("d-std", TaxCategory.STANDARD));
    seed.observeGold("60.00");
  }

  @Test
  void unpricedProductHasNoPriceFieldAndNoZeroAmount() {
    client
        .get()
        .uri("/api/catalog/products/d-onreq")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(
            body -> {
              assertFalse(body.contains("\"price\""), body);
              assertFalse(body.contains("0.00"), body);
            });
  }

  @Test
  void pricedProductWithNoApplicableRuleIsOnRequest() {
    final Category unruled = seed.category("d-unruled", TaxCategory.STANDARD);
    seed.pricedProduct("d-unruled-1", "Unruled item", unruled);

    client
        .get()
        .uri("/api/catalog/products/d-unruled-1")
        .exchange()
        .expectBody()
        .jsonPath("$.pricingMode")
        .isEqualTo("ON_REQUEST")
        .jsonPath("$.price")
        .doesNotExist();
  }

  @Test
  void detailCarriesEveryFr13Field() {
    client
        .get()
        .uri("/api/catalog/products/d-priced")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Detail gold bar")
        .jsonPath("$.purity")
        .isEqualTo("999.9")
        .jsonPath("$.weight.amount")
        .isEqualTo("100")
        .jsonPath("$.weight.unit")
        .isEqualTo("GRAM")
        .jsonPath("$.stock")
        .isEqualTo("IN_STOCK")
        .jsonPath("$.pricingMode")
        .isEqualTo("FIXED")
        .jsonPath("$.price.amount")
        .isNotEmpty()
        .jsonPath("$.tax.category")
        .isEqualTo("INVESTMENT_GRADE")
        .jsonPath("$.tax.ratePercent")
        .isEqualTo("0");
  }

  @Test
  void standardRatedDetailShowsTheRate() {
    client
        .get()
        .uri("/api/catalog/products/d-standard")
        .exchange()
        .expectBody()
        .jsonPath("$.tax.category")
        .isEqualTo("STANDARD")
        .jsonPath("$.tax.ratePercent")
        .isEqualTo("19");
  }

  @Test
  void theSellablePriceIsWhatPriceDerivationGives() {
    final String expected =
        PriceDerivation.derive(
                cache.find(priced.specification().metal()).orElseThrow().latest(),
                priced.specification(),
                rule)
            .unitPrice()
            .amount()
            .toPlainString();

    client
        .get()
        .uri("/api/catalog/products/d-priced")
        .exchange()
        .expectBody()
        .jsonPath("$.price.amount")
        .isEqualTo(expected);
    client
        .get()
        .uri("/api/catalog/categories/d-bars/products")
        .exchange()
        .expectBody()
        .jsonPath("$.items[0].price.amount")
        .isEqualTo(expected);
  }

  @Test
  void theResponseNeverLeaksTheMarginOrTheReferencePrice() {
    client
        .get()
        .uri("/api/catalog/products/d-priced")
        .exchange()
        .expectBody(String.class)
        .value(
            body -> {
              assertFalse(body.contains("margin"), body);
              assertFalse(body.contains("reference"), body);
              assertFalse(body.contains("rule"), body);
            });
  }

  @Test
  void unknownProductIs404WithTheEnvelope() {
    client
        .get()
        .uri("/api/catalog/products/no-such-product")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath(CODE)
        .isEqualTo("product.not-found")
        .jsonPath("$.correlationId")
        .isNotEmpty();
  }

  @Test
  void malformedIdIs400() {
    client
        .get()
        .uri("/api/catalog/products/bad%20id")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath(CODE)
        .isEqualTo("id.malformed");
  }
}
