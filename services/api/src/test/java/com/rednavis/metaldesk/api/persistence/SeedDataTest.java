package com.rednavis.metaldesk.api.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.persistence.repository.CategoryRepository;
import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.persistence.document.CategoryDocument;
import com.rednavis.metaldesk.persistence.document.PriceDocument.ScopeKind;
import com.rednavis.metaldesk.persistence.document.PriceRuleDocument;
import com.rednavis.metaldesk.persistence.mapper.CategoryMapper;
import com.rednavis.metaldesk.persistence.mapper.ProductMapper;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

/**
 * The data the migrations seed, read back through the real mappers: every category and product must
 * load into the domain model, and every priced one must have a margin rule to be sold.
 */
class SeedDataTest extends MongoTestSupport {

  @Autowired private CategoryRepository categories;
  @Autowired private ProductRepository products;
  @Autowired private CategoryMapper categoryMapper;
  @Autowired private ProductMapper productMapper;
  @Autowired private ReactiveMongoTemplate mongo;

  @Test
  void seededCatalogueLoadsIntoTheDomainModel() {
    final List<CategoryDocument> stored =
        Objects.requireNonNull(categories.findAll().collectList().block());
    final Map<String, Category> byId =
        stored.stream().collect(Collectors.toMap(CategoryDocument::id, categoryMapper::toDomain));
    assertTrue(byId.size() >= 9);

    final List<Product> loaded =
        Objects.requireNonNull(products.findAll().collectList().block()).stream()
            .map(doc -> productMapper.toDomain(doc, byId.get(doc.categoryId())))
            .toList();
    assertTrue(loaded.size() >= 9);
  }

  @Test
  void everySeededPricedProductHasMarginRule() {
    final List<String> ruleIds =
        Objects.requireNonNull(mongo.findAll(PriceRuleDocument.class).collectList().block())
            .stream()
            .map(PriceRuleDocument::id)
            .toList();

    Objects.requireNonNull(products.findAll().collectList().block()).stream()
        .filter(doc -> doc.price() != null && doc.id().startsWith("prod-"))
        .forEach(
            doc ->
                assertTrue(
                    ruleIds.contains(PriceRuleDocument.idFor(ScopeKind.CATEGORY, doc.categoryId())),
                    "no margin rule for " + doc.id()));
  }
}
