package com.rednavis.metaldesk.api.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rednavis.metaldesk.api.persistence.MongoTestSupport;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The category listing, product detail and their error cases, over HTTP against real MongoDB. */
@AutoConfigureWebTestClient
@Import(CatalogSeed.class)
class CatalogListingTest extends MongoTestSupport {

  private static final int PAGE_SIZE = 10;
  private static final int MANY = 25;
  private static final String CODE = "$.code";

  @Autowired private WebTestClient client;
  @Autowired private CatalogSeed seed;

  @BeforeEach
  void seedCatalog() {
    final Category bars = seed.category("l-bars", TaxCategory.INVESTMENT_GRADE);
    seed.marginForCategory(bars, "5");
    seed.pricedProduct("l-priced", "Listing gold bar", bars);
    seed.unpricedProduct("l-onreq", "Listing platinum coin", bars);
    seed.observeGold("60.00");
    final Category many = seed.category("l-many", TaxCategory.STANDARD);
    for (int i = 0; i < MANY; i++) {
      seed.pricedProduct("l-many-%02d".formatted(i), "Many item %02d".formatted(i), many);
    }
    seed.marginForCategory(many, "10");
  }

  @Test
  void categoriesAreListedByName() {
    client
        .get()
        .uri("/api/catalog/categories")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[?(@.id=='l-bars')].taxCategory")
        .isEqualTo("INVESTMENT_GRADE")
        .jsonPath("$[?(@.id=='l-bars')].parentId")
        .doesNotExist();
  }

  @Test
  void categoryListingCarriesPricingModeOnEveryEntry() {
    client
        .get()
        .uri("/api/catalog/categories/l-bars/products")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.total")
        .isEqualTo(2)
        .jsonPath("$.items[0].id")
        .isEqualTo("l-priced")
        .jsonPath("$.items[0].pricingMode")
        .isEqualTo("FIXED")
        .jsonPath("$.items[0].price.currency")
        .isEqualTo("EUR")
        .jsonPath("$.items[1].id")
        .isEqualTo("l-onreq")
        .jsonPath("$.items[1].pricingMode")
        .isEqualTo("ON_REQUEST");
  }

  @Test
  void unpricedEntryHasNoPriceFieldAndNoZeroAmount() {
    client
        .get()
        .uri("/api/catalog/categories/l-bars/products")
        .exchange()
        .expectBody()
        .jsonPath("$.items[1].price")
        .doesNotExist();
    client
        .get()
        .uri("/api/catalog/products/l-onreq")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(
            body -> {
              assertFalse(body.contains("\"price\""), body);
              assertFalse(body.contains("0.00"), body);
              assertFalse(body.contains("\"amount\":\"0"), body);
            });
  }

  @Test
  void listingIsPaged() {
    client
        .get()
        .uri("/api/catalog/categories/l-many/products?page=2&size=" + PAGE_SIZE)
        .exchange()
        .expectBody()
        .jsonPath("$.total")
        .isEqualTo(MANY)
        .jsonPath("$.page")
        .isEqualTo(2)
        .jsonPath("$.size")
        .isEqualTo(PAGE_SIZE)
        .jsonPath("$.items.length()")
        .isEqualTo(MANY - 2 * PAGE_SIZE)
        .jsonPath("$.items[0].id")
        .isEqualTo("l-many-20");
  }

  @Test
  void anOversizedPageIsRefused() {
    client
        .get()
        .uri("/api/catalog/categories/l-many/products?size=" + (CatalogService.MAX_PAGE_SIZE + 1))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath(CODE)
        .isEqualTo("page.invalid");
  }

  @Test
  void unknownCategoryIs404() {
    client
        .get()
        .uri("/api/catalog/categories/no-such-category/products")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath(CODE)
        .isEqualTo("category.not-found");
  }
}
