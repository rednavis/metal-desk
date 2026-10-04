package com.rednavis.metaldesk.migrations.changes;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import org.bson.Document;

/**
 * Adds a wider range of test products: more gold, silver, platinum and palladium bars and coins in
 * different weights, prices and stock states, so the storefront, the cart and the price rules have
 * something realistic to work on. Products with no price are priced from the live reference price
 * and the margin rules; the rest carry a fixed price.
 *
 * <p>Every id starts with {@code prod-x-} (and the one new category with {@code cat-platinum-
 * coins}), so the rollback removes exactly these and nothing from the earlier seeds.
 */
@ChangeUnit(id = "seed-more-products", order = "005", author = "metaldesk")
public class C005SeedMoreProducts {

  private static final String ID = "_id";
  private static final String CATEGORIES = "categories";
  private static final String PRODUCTS = "products";
  private static final String CATEGORY_RULE_ID = "CATEGORY:" + "cat-platinum-coins";
  private static final String PRICE_RULES = "price_rules";
  private static final String STOCK = "stock";
  private static final String PRICE = "price";
  private static final String PLATINUM_COINS = "cat-platinum-coins";
  private static final String GOLD_BARS = "cat-gold-bars";
  private static final String GOLD_COINS = "cat-gold-coins";
  private static final String SILVER_BARS = "cat-silver-bars";
  private static final String SILVER_COINS = "cat-silver-coins";
  private static final String PLATINUM_BARS = "cat-platinum-bars";
  private static final String GOLD = "GOLD";
  private static final String SILVER = "SILVER";
  private static final String PLATINUM = "PLATINUM";
  private static final String PALLADIUM = "PALLADIUM";
  private static final String TROY_OUNCE = "TROY_OUNCE";
  private static final String GRAM = "GRAM";
  private static final String KILOGRAM = "KILOGRAM";
  private static final String IN_STOCK = "IN_STOCK";
  private static final String OUT_OF_STOCK = "OUT_OF_STOCK";
  private static final String ON_REQUEST = "ON_REQUEST";
  private static final String PURITY_GOLD = "999.9";
  private static final String PURITY_SILVER = "999";
  private static final String PURITY_PGM = "999.5";
  private static final String ID_PREFIX = "^prod-x-";

  /** Creates the change unit; Mongock instantiates it. */
  public C005SeedMoreProducts() {
    // Nothing to set up: Mongock injects what the methods ask for.
  }

  /**
   * Inserts the category and the products.
   *
   * @param database the database being migrated
   */
  @Execution
  public void execute(MongoDatabase database) {
    database
        .getCollection(CATEGORIES)
        .insertOne(
            new Document(ID, PLATINUM_COINS)
                .append("name", "Platinum coins")
                .append("parentId", "cat-platinum-group")
                .append("taxCategory", "STANDARD"));
    database
        .getCollection(PRICE_RULES)
        .insertOne(
            new Document(ID, CATEGORY_RULE_ID)
                .append("scopeKind", "CATEGORY")
                .append("scopeId", PLATINUM_COINS)
                .append("marginPercent", "8"));
    final MongoCollection<Document> products = database.getCollection(PRODUCTS);
    products.insertMany(
        List.of(
            // Gold
            product("prod-x-gold-bar-1g", "Gold bar 1 g", GOLD_BARS, GOLD, PURITY_GOLD, "1", GRAM)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("98.00")),
            product(
                    "prod-x-gold-bar-10g",
                    "Gold bar 10 g",
                    GOLD_BARS,
                    GOLD,
                    PURITY_GOLD,
                    "10",
                    GRAM)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("720.00")),
            product(
                    "prod-x-gold-bar-50g",
                    "Gold bar 50 g",
                    GOLD_BARS,
                    GOLD,
                    PURITY_GOLD,
                    "50",
                    GRAM)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("3480.00")),
            product(
                    "prod-x-gold-bar-250g",
                    "Gold bar 250 g",
                    GOLD_BARS,
                    GOLD,
                    PURITY_GOLD,
                    "250",
                    GRAM)
                .append(STOCK, ON_REQUEST),
            product(
                    "prod-x-gold-coin-half-oz",
                    "Gold bullion coin 1/2 oz",
                    GOLD_COINS,
                    GOLD,
                    "916.7",
                    "15.55",
                    GRAM)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("1190.00")),
            product(
                    "prod-x-gold-coin-tenth-oz",
                    "Gold bullion coin 1/10 oz",
                    GOLD_COINS,
                    GOLD,
                    "916.7",
                    "3.11",
                    GRAM)
                .append(STOCK, OUT_OF_STOCK)
                .append(PRICE, eur("265.00")),
            // Silver
            product(
                    "prod-x-silver-bar-250g",
                    "Silver bar 250 g",
                    SILVER_BARS,
                    SILVER,
                    PURITY_SILVER,
                    "250",
                    GRAM)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("289.00")),
            product(
                    "prod-x-silver-bar-5kg",
                    "Silver bar 5 kg",
                    SILVER_BARS,
                    SILVER,
                    PURITY_SILVER,
                    "5",
                    KILOGRAM)
                .append(STOCK, ON_REQUEST),
            product(
                    "prod-x-silver-coin-half-oz",
                    "Silver bullion coin 1/2 oz",
                    SILVER_COINS,
                    SILVER,
                    PURITY_SILVER,
                    "15.55",
                    GRAM)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("19.50")),
            product(
                    "prod-x-silver-coin-10oz",
                    "Silver bullion coin 10 oz",
                    SILVER_COINS,
                    SILVER,
                    PURITY_SILVER,
                    "10",
                    TROY_OUNCE)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("345.00")),
            // Platinum and palladium
            product(
                    "prod-x-platinum-bar-100g",
                    "Platinum bar 100 g",
                    PLATINUM_BARS,
                    PLATINUM,
                    PURITY_PGM,
                    "100",
                    GRAM)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("3180.00")),
            product(
                    "prod-x-platinum-coin-1oz",
                    "Platinum bullion coin 1 oz",
                    PLATINUM_COINS,
                    PLATINUM,
                    PURITY_PGM,
                    "1",
                    TROY_OUNCE)
                .append(STOCK, IN_STOCK)
                .append(PRICE, eur("1090.00")),
            product(
                    "prod-x-palladium-bar-100g",
                    "Palladium bar 100 g",
                    "cat-palladium-bars",
                    PALLADIUM,
                    PURITY_PGM,
                    "100",
                    GRAM)
                .append(STOCK, OUT_OF_STOCK)));
  }

  /**
   * Removes what {@link #execute} added, and only that.
   *
   * @param database the database being migrated
   */
  @RollbackExecution
  public void rollback(MongoDatabase database) {
    database
        .getCollection(PRODUCTS)
        .deleteMany(new Document(ID, new Document("$regex", ID_PREFIX)));
    database.getCollection(PRICE_RULES).deleteOne(new Document(ID, CATEGORY_RULE_ID));
    database.getCollection(CATEGORIES).deleteOne(new Document(ID, PLATINUM_COINS));
  }

  private static Document product(
      String id,
      String name,
      String category,
      String metal,
      String purity,
      String amount,
      String unit) {
    return new Document(ID, id)
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
