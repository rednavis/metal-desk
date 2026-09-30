package com.rednavis.metaldesk.api.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;

/** The indexes each document declares really exist in MongoDB once the application has started. */
class DeclaredIndexTest extends MongoTestSupport {

  @Autowired private ReactiveMongoTemplate mongo;

  @Test
  void orderNumberIsUniqueAndCustomerIdIsIndexed() {
    assertTrue(hasIndexOn("orders", "number", true));
    assertTrue(hasIndexOn("orders", "customerId", false));
  }

  @Test
  void customerEmailIsUnique() {
    assertTrue(hasIndexOn("customers", "email", true));
  }

  @Test
  void catalogFieldsThatQueriesFilterOnAreIndexed() {
    assertTrue(hasIndexOn("products", "categoryId", false));
    assertTrue(hasIndexOn("categories", "parentId", false));
    assertTrue(hasIndexOn("fulfillment_tiers", "region", false));
  }

  @Test
  void productNameCarriesTextIndex() {
    assertTrue(
        indexes("products").stream()
            .anyMatch(index -> subMap(index, "weights").containsKey("name")));
  }

  private List<Map<String, Object>> indexes(String collection) {
    return mongo
        .getCollection(collection)
        .flatMapMany(found -> Flux.from(found.listIndexes()))
        .<Map<String, Object>>map(index -> index)
        .collectList()
        .block();
  }

  private boolean hasIndexOn(String collection, String field, boolean unique) {
    return indexes(collection).stream()
        .anyMatch(
            index ->
                Boolean.TRUE.equals(index.getOrDefault("unique", false)) == unique
                    && subMap(index, "key").containsKey(field));
  }

  private static Map<?, ?> subMap(Map<String, Object> index, String name) {
    return index.get(name) instanceof Map<?, ?> found ? found : Map.of();
  }
}
