package com.rednavis.metaldesk.migrations.changes;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import org.bson.Document;

/**
 * Seeds the {@code products} collection with bullion bars and coins, so the seeded carts have real
 * products to point at and the storefront has something to browse.
 *
 * <p>The mix covers every stock status and both ways a product is priced: a fixed price, and no
 * price at all for a product sold on request, which sends an order to a manager quote (BRD FR-5.3).
 * Prices are indicative demo figures in EUR, not market data.
 */
@ChangeUnit(id = "seed-products", order = "003", author = "metaldesk")
public class C003SeedProducts {

  private static final String DIMENSIONS = "dimensions";
  private static final String STOCK = "stock";
  private static final String PRICE = "price";
  private static final String IN_STOCK = "IN_STOCK";
  private static final String OUT_OF_STOCK = "OUT_OF_STOCK";
  private static final String ON_REQUEST = "ON_REQUEST";
  private static final String TROY_OUNCE = "TROY_OUNCE";
  private static final String GRAM = "GRAM";
  private static final String KILOGRAM = "KILOGRAM";

  /** Creates the change unit; Mongock instantiates it. */
  public C003SeedProducts() {
    // Nothing to set up: Mongock injects what the methods ask for.
  }

  /**
   * Inserts the products.
   *
   * @param database the database being migrated
   */
  @Execution
  public void execute(MongoDatabase database) {
    final MongoCollection<Document> products = database.getCollection("products");
    products.insertMany(
        List.of(
            gold("prod-gold-bar-1oz", "Gold bar 1 oz", "cat-gold-bars", "1", TROY_OUNCE)
                .append(DIMENSIONS, "23.5 x 14 x 1 mm")
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("2150.00")),
            gold("prod-gold-bar-100g", "Gold bar 100 g", "cat-gold-bars", "100", GRAM)
                .append(DIMENSIONS, "50 x 28 x 2 mm")
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("6890.00")),
            gold("prod-gold-bar-1kg", "Gold bar 1 kg", "cat-gold-bars", "1", KILOGRAM)
                .append(DIMENSIONS, "117 x 52 x 8 mm")
                .append(STOCK, ON_REQUEST),
            gold("prod-gold-coin-1oz", "Gold bullion coin 1 oz", "cat-gold-coins", "1", TROY_OUNCE)
                .append(DIMENSIONS, "32.7 mm diameter")
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("2240.00")),
            silver(
                    "prod-silver-coin-1oz",
                    "Silver bullion coin 1 oz",
                    "cat-silver-coins",
                    "1",
                    TROY_OUNCE)
                .append(DIMENSIONS, "38.1 mm diameter")
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("34.90")),
            silver("prod-silver-bar-100g", "Silver bar 100 g", "cat-silver-bars", "100", GRAM)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("118.00")),
            silver("prod-silver-bar-1kg", "Silver bar 1 kg", "cat-silver-bars", "1", KILOGRAM)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("1150.00")),
            metal(
                    "prod-platinum-bar-1oz",
                    "Platinum bar 1 oz",
                    "cat-platinum-bars",
                    "PLATINUM",
                    "999.5",
                    "1",
                    TROY_OUNCE)
                .append(STOCK, OUT_OF_STOCK)
                .append(PRICE, eur("1010.00")),
            metal(
                    "prod-palladium-bar-1oz",
                    "Palladium bar 1 oz",
                    "cat-palladium-bars",
                    "PALLADIUM",
                    "999.5",
                    "1",
                    TROY_OUNCE)
                .append(STOCK, ON_REQUEST)));
  }

  /**
   * Removes the seeded products, and only those.
   *
   * @param database the database being migrated
   */
  @RollbackExecution
  public void rollback(MongoDatabase database) {
    database
        .getCollection("products")
        .deleteMany(new Document("_id", new Document("$regex", "^prod-")));
  }

  private static Document gold(
      String id, String name, String category, String amount, String unit) {
    return metal(id, name, category, "GOLD", "999.9", amount, unit);
  }

  private static Document silver(
      String id, String name, String category, String amount, String unit) {
    return metal(id, name, category, "SILVER", "999", amount, unit);
  }

  private static Document metal(
      String id,
      String name,
      String category,
      String metal,
      String purity,
      String amount,
      String unit) {
    return new Document("_id", id)
        .append("name", name)
        .append("categoryId", category)
        .append("metal", metal)
        .append("purity", purity)
        .append("weight", new Document("amount", amount).append("unit", unit));
  }

  private static Document eur(String amount) {
    return new Document("amount", amount).append("currency", "EUR");
  }
}
