package com.rednavis.metaldesk.api.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.catalog.dto.ProductSummaryView;
import com.rednavis.metaldesk.api.persistence.MongoTestSupport;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

/** BR-1's cap on related products, and free-text search, over HTTP against real MongoDB. */
@AutoConfigureWebTestClient
@Import(CatalogSeed.class)
class CatalogRelatedAndSearchTest extends MongoTestSupport {

  @Autowired private WebTestClient client;
  @Autowired private CatalogSeed seed;

  @BeforeEach
  void seedCatalog() {
    final Category crowded = seed.category("r-crowded", TaxCategory.STANDARD);
    for (int i = 0; i < CatalogService.RELATED_CAP + 5; i++) {
      seed.pricedProduct("r-crowded-%02d".formatted(i), "Crowded item %02d".formatted(i), crowded);
    }
    final Category other = seed.category("r-other", TaxCategory.STANDARD);
    seed.pricedProduct("r-other-1", "Elsewhere item", other);
    seed.pricedProduct("r-search-1", "Zorbium bar 100 g", other);
    seed.pricedProduct("r-search-2", "Odd (1+1) coin", other);
    seed.unpricedProduct("r-search-3", "ZORBIUM ingot", other);
  }

  @Test
  void relatedProductsShareTheCategoryExcludeTheSubjectAndAreCapped() {
    final List<ProductSummaryView> related =
        client
            .get()
            .uri("/api/catalog/products/r-crowded-03/related")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(ProductSummaryView.class)
            .returnResult()
            .getResponseBody();

    assertEquals(CatalogService.RELATED_CAP, related.size());
    assertTrue(related.stream().allMatch(entry -> "r-crowded".equals(entry.categoryId())));
    assertFalse(related.stream().anyMatch(entry -> "r-crowded-03".equals(entry.id())));
  }

  @Test
  void productAloneInItsCategoryHasNoRelatedProducts() {
    client
        .get()
        .uri("/api/catalog/products/r-other-1/related")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[?(@.categoryId!='r-other')]")
        .doesNotExist();
  }

  @Test
  void relatedOfAnUnknownProductIs404() {
    client.get().uri("/api/catalog/products/nope/related").exchange().expectStatus().isNotFound();
  }

  @Test
  void searchFindsProductBySubstringOfItsNameIgnoringCase() {
    client
        .get()
        .uri("/api/catalog/search?q=orbium")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(2)
        .jsonPath("$[0].name")
        .isEqualTo("ZORBIUM ingot")
        .jsonPath("$[1].name")
        .isEqualTo("Zorbium bar 100 g");
  }

  @Test
  void searchTreatsRegexCharactersLiterally() {
    client
        .get()
        .uri("/api/catalog/search?q={q}", "(1+1)")
        .exchange()
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(1)
        .jsonPath("$[0].id")
        .isEqualTo("r-search-2");
  }

  @Test
  void searchWithNoMatchIsAnEmptyListNotA404() {
    client
        .get()
        .uri("/api/catalog/search?q=qqqzzzqqq")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
  }

  @Test
  void tooShortQueryIs400() {
    client
        .get()
        .uri("/api/catalog/search?q=z")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("search.query-invalid");
  }
}
