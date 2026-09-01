package com.rednavis.metaldesk.api.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.persistence.repository.CategoryRepository;
import com.rednavis.metaldesk.api.persistence.repository.FulfillmentTierRepository;
import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.persistence.document.ProductDocument;
import com.rednavis.metaldesk.persistence.fixtures.CatalogFixtures;
import com.rednavis.metaldesk.persistence.mapper.CategoryMapper;
import com.rednavis.metaldesk.persistence.mapper.FulfillmentTierMapper;
import com.rednavis.metaldesk.persistence.mapper.ProductMapper;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.TextCriteria;
import reactor.test.StepVerifier;

/** Categories, products and fulfillment tiers survive a real trip through MongoDB. */
class CatalogPersistenceTest extends MongoTestSupport {

  @Autowired private CategoryRepository categoryRepo;
  @Autowired private ProductRepository productRepo;
  @Autowired private FulfillmentTierRepository tierRepo;

  private final CategoryMapper categories = new CategoryMapper();
  private final ProductMapper products = new ProductMapper();
  private final FulfillmentTierMapper tiers = new FulfillmentTierMapper();

  @Test
  void categoryIsFoundByItsParent() {
    final Category category = CatalogFixtures.childCategory();

    StepVerifier.create(
            categoryRepo
                .save(categories.toDocument(category))
                .thenMany(categoryRepo.findByParentId("cat-bars"))
                .map(categories::toDomain))
        .expectNext(category)
        .verifyComplete();
  }

  @Test
  void productIsFoundByCategoryAndBySearchingItsName() {
    final Category category = CatalogFixtures.childCategory();
    final Product product = CatalogFixtures.fixedPriceProduct("p-store-1", category);

    StepVerifier.create(
            productRepo
                .save(products.toDocument(product))
                .thenMany(productRepo.findByCategoryId("cat-silver"))
                .map(document -> products.toDomain(document, category)))
        .expectNext(product)
        .verifyComplete();
    StepVerifier.create(
            productRepo
                .findAllBy(TextCriteria.forDefaultLanguage().matching("gold"))
                .map(ProductDocument::id)
                .collectList())
        .assertNext(ids -> assertTrue(ids.contains("p-store-1")))
        .verifyComplete();
  }

  @Test
  void tierIsFoundByRegion() {
    final FulfillmentTier tier = CatalogFixtures.tier("t-store-1", "XA");

    StepVerifier.create(
            tierRepo
                .save(tiers.toDocument(tier))
                .thenMany(tierRepo.findByRegion("XA"))
                .map(tiers::toDomain))
        .expectNext(tier)
        .verifyComplete();
  }
}
