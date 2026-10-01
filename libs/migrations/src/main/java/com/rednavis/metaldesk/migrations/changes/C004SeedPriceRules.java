package com.rednavis.metaldesk.migrations.changes;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import org.bson.Document;

/**
 * Seeds the {@code price_rules} collection with one margin per seeded category (BRD BR-3).
 *
 * <p>Without a rule a product has no sellable price and the storefront shows it as on request, so a
 * fresh database would sell nothing. A rule applies to the category it names and not to its
 * children, so every category that holds products gets its own. The margins are demonstration
 * figures, not business data.
 */
@ChangeUnit(id = "seed-price-rules", order = "005", author = "metaldesk")
public class C004SeedPriceRules {

  private static final String CATEGORY = "CATEGORY";

  /** Creates the change unit; Mongock instantiates it. */
  public C004SeedPriceRules() {
    // Nothing to set up: Mongock injects what the methods ask for.
  }

  /**
   * Inserts the rules.
   *
   * @param database the database being migrated
   */
  @Execution
  public void execute(MongoDatabase database) {
    database
        .getCollection("price_rules")
        .insertMany(
            List.of(
                rule("cat-gold-bars", "3"),
                rule("cat-gold-coins", "5"),
                rule("cat-silver-bars", "8"),
                rule("cat-silver-coins", "12"),
                rule("cat-platinum-bars", "6"),
                rule("cat-palladium-bars", "6")));
  }

  /**
   * Removes the seeded rules, and only those.
   *
   * @param database the database being migrated
   */
  @RollbackExecution
  public void rollback(MongoDatabase database) {
    database
        .getCollection("price_rules")
        .deleteMany(new Document("_id", new Document("$regex", "^CATEGORY:cat-")));
  }

  private static Document rule(String categoryId, String marginPercent) {
    return new Document("_id", CATEGORY + ":" + categoryId)
        .append("scopeKind", CATEGORY)
        .append("scopeId", categoryId)
        .append("marginPercent", marginPercent);
  }
}
