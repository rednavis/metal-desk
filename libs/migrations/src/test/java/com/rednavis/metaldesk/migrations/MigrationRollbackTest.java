package com.rednavis.metaldesk.migrations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.rednavis.metaldesk.migrations.changes.C001CreateUsers;
import com.rednavis.metaldesk.migrations.changes.C002SeedCategories;
import com.rednavis.metaldesk.migrations.changes.C003SeedProducts;
import com.rednavis.metaldesk.migrations.changes.C004SeedPriceRules;
import com.rednavis.metaldesk.migrations.changes.C005SeedMoreProducts;
import com.rednavis.metaldesk.persistence.testing.SharedMongo;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** Each change can be undone, and the startup step runs the migrations only when it is enabled. */
class MigrationRollbackTest {

  private static final String PRICE_RULES = "price_rules";
  private static final String PRODUCTS = "products";
  private static final String CATEGORIES = "categories";
  private static final String USERS = "users";

  private static String uri(String database) {
    return SharedMongo.connectionString().replace("/metaldesk", "/" + database);
  }

  private static String freshDatabase() {
    return "rollback_" + UUID.randomUUID().toString().replace("-", "");
  }

  @Test
  void rollingBackInReverseOrderEmptiesWhatTheChangesCreated() {
    final String name = freshDatabase();
    MongoMigrations.run(uri(name));

    try (MongoClient client = MongoClients.create(uri(name))) {
      final MongoDatabase database = client.getDatabase(name);
      assertTrue(database.getCollection(PRICE_RULES).countDocuments() > 0);
      assertTrue(database.getCollection(PRODUCTS).countDocuments() > 0);
      assertTrue(database.getCollection(CATEGORIES).countDocuments() > 0);

      assertEquals(
          1,
          database
              .getCollection(CATEGORIES)
              .countDocuments(new Document("_id", "cat-platinum-coins")));
      final long before = database.getCollection(PRODUCTS).countDocuments();
      new C005SeedMoreProducts().rollback(database);
      assertTrue(database.getCollection(PRODUCTS).countDocuments() < before);
      assertEquals(
          0,
          database
              .getCollection(CATEGORIES)
              .countDocuments(new Document("_id", "cat-platinum-coins")));

      new C004SeedPriceRules().rollback(database);
      new C003SeedProducts().rollback(database);
      new C002SeedCategories().rollback(database);
      new C001CreateUsers().rollback(database);

      assertEquals(0, database.getCollection(PRICE_RULES).countDocuments());
      assertEquals(0, database.getCollection(PRODUCTS).countDocuments());
      assertEquals(0, database.getCollection(CATEGORIES).countDocuments());
      assertEquals(0, database.getCollection(USERS).countDocuments());
    }
  }

  @Test
  void theStartupStepMigratesWhenEnabled() {
    final String name = freshDatabase();

    new MigrationsConfiguration()
        .mongoMigrationsRunner(uri(name), true)
        .afterSingletonsInstantiated();

    try (MongoClient client = MongoClients.create(uri(name))) {
      assertEquals(2, client.getDatabase(name).getCollection(USERS).countDocuments());
    }
  }

  @Test
  void theStartupStepDoesNothingWhenDisabled() {
    final String name = freshDatabase();

    assertDoesNotThrow(
        () ->
            new MigrationsConfiguration()
                .mongoMigrationsRunner("mongodb://unreachable.invalid/none", false)
                .afterSingletonsInstantiated());

    try (MongoClient client = MongoClients.create(uri(name))) {
      assertEquals(0, client.getDatabase(name).getCollection(USERS).countDocuments());
    }
  }
}
