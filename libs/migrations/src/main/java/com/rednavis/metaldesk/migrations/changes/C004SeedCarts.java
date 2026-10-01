package com.rednavis.metaldesk.migrations.changes;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.Instant;
import java.util.List;
import org.bson.Document;

/**
 * Seeds the {@code carts} collection with four anonymous carts, each a situation the storefront has
 * to handle: a first small purchase, a bulk silver order, a cart holding a product sold on request
 * (which ends in a manager quote), and an empty cart.
 *
 * <p>A cart's id is a capability, so these fixed, readable ids are for development only. The carts
 * have no owner (the {@code ownerId} field is left out, not null, because its unique index is
 * sparse); a customer who signs in with one takes it over. They reference the products of {@link
 * C003SeedProducts}.
 */
@ChangeUnit(id = "seed-carts", order = "004", author = "metaldesk")
public class C004SeedCarts {

  /** Creates the change unit; Mongock instantiates it. */
  public C004SeedCarts() {
    // Nothing to set up: Mongock injects what the methods ask for.
  }

  /**
   * Inserts the carts.
   *
   * @param database the database being migrated
   */
  @Execution
  public void execute(MongoDatabase database) {
    final MongoCollection<Document> carts = database.getCollection("carts");
    final Instant now = Instant.now();
    carts.insertMany(
        List.of(
            cart(
                "demo-cart-first-purchase",
                now,
                line("prod-gold-bar-1oz", 1),
                line("prod-silver-coin-1oz", 2)),
            cart(
                "demo-cart-bulk-silver",
                now,
                line("prod-silver-coin-1oz", 20),
                line("prod-silver-bar-1kg", 2)),
            cart(
                "demo-cart-manager-quote",
                now,
                line("prod-gold-bar-1kg", 1),
                line("prod-gold-bar-100g", 3)),
            cart("demo-cart-empty", now)));
  }

  /**
   * Removes the seeded carts, and only those.
   *
   * @param database the database being migrated
   */
  @RollbackExecution
  public void rollback(MongoDatabase database) {
    database
        .getCollection("carts")
        .deleteMany(new Document("_id", new Document("$regex", "^demo-cart-")));
  }

  private static Document cart(String id, Instant now, Document... lines) {
    return new Document("_id", id)
        .append("lines", List.of(lines))
        .append("version", 0L)
        .append("createdAt", now)
        .append("updatedAt", now);
  }

  private static Document line(String productId, int quantity) {
    return new Document("productId", productId).append("quantity", quantity);
  }
}
