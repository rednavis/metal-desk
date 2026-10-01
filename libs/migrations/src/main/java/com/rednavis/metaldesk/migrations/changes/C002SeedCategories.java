package com.rednavis.metaldesk.migrations.changes;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import org.bson.Document;

/**
 * Seeds the {@code categories} collection with a small precious-metals catalogue tree: one root per
 * metal family, and the forms it is sold in (bars, coins) beneath it.
 *
 * <p>The tax category follows the metal: investment gold is zero-rated, silver and the platinum
 * group carry the standard rate (Architecture section 3). A child has its parent's category. Ids
 * are fixed, readable slugs, so the products and carts seeded after this can refer to them.
 *
 * <p>No index is created here: the services declare category indexes from the document class.
 */
@ChangeUnit(id = "seed-categories", order = "002", author = "metaldesk")
public class C002SeedCategories {

  private static final String INVESTMENT = "INVESTMENT_GRADE";
  private static final String STANDARD = "STANDARD";

  /** Creates the change unit; Mongock instantiates it. */
  public C002SeedCategories() {
    // Nothing to set up: Mongock injects what the methods ask for.
  }

  /**
   * Inserts the categories.
   *
   * @param database the database being migrated
   */
  @Execution
  public void execute(MongoDatabase database) {
    final MongoCollection<Document> categories = database.getCollection("categories");
    categories.insertMany(
        List.of(
            root("cat-gold", "Gold", INVESTMENT),
            child("cat-gold-bars", "Gold bars", "cat-gold", INVESTMENT),
            child("cat-gold-coins", "Gold coins", "cat-gold", INVESTMENT),
            root("cat-silver", "Silver", STANDARD),
            child("cat-silver-bars", "Silver bars", "cat-silver", STANDARD),
            child("cat-silver-coins", "Silver coins", "cat-silver", STANDARD),
            root("cat-platinum-group", "Platinum group metals", STANDARD),
            child("cat-platinum-bars", "Platinum bars", "cat-platinum-group", STANDARD),
            child("cat-palladium-bars", "Palladium bars", "cat-platinum-group", STANDARD)));
  }

  /**
   * Removes the seeded categories, and only those.
   *
   * @param database the database being migrated
   */
  @RollbackExecution
  public void rollback(MongoDatabase database) {
    database
        .getCollection("categories")
        .deleteMany(new Document("_id", new Document("$regex", "^cat-")));
  }

  private static Document root(String id, String name, String taxCategory) {
    return new Document("_id", id).append("name", name).append("taxCategory", taxCategory);
  }

  private static Document child(String id, String name, String parentId, String taxCategory) {
    return root(id, name, taxCategory).append("parentId", parentId);
  }
}
