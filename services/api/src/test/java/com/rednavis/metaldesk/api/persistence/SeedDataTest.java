package com.rednavis.metaldesk.api.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.persistence.repository.CategoryRepository;
import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.persistence.document.CartDocument;
import com.rednavis.metaldesk.persistence.document.CategoryDocument;
import com.rednavis.metaldesk.persistence.document.ProductDocument;
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
 * The data the migrations seed, read back through the real mappers: every category, product and
 * cart must load into the domain model, and the carts must point only at products that exist.
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
  void seededCartsReferenceOnlyExistingProducts() {
    final Map<String, Boolean> known =
        Objects.requireNonNull(products.findAll().collectList().block()).stream()
            .collect(Collectors.toMap(ProductDocument::id, doc -> true));

    for (final String id :
        List.of(
            "demo-cart-first-purchase",
            "demo-cart-bulk-silver",
            "demo-cart-manager-quote",
            "demo-cart-empty")) {
      final CartDocument seeded = cart(id);
      assertNull(seeded.ownerId());
      seeded.lines().forEach(line -> assertTrue(known.containsKey(line.productId()), id));
    }
    assertEquals(2, cart("demo-cart-first-purchase").lines().size());
    assertTrue(cart("demo-cart-empty").lines().isEmpty());
  }

  private CartDocument cart(String id) {
    return Objects.requireNonNull(mongo.findById(id, CartDocument.class).block(), id);
  }
}
