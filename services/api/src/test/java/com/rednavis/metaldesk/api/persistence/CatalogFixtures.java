package com.rednavis.metaldesk.api.persistence;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.catalog.ProductSpecification;
import com.rednavis.metaldesk.share.domain.catalog.StockStatus;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;
import com.rednavis.metaldesk.share.domain.fulfillment.TransitTime;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.measure.Purity;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.util.Optional;

/** Small synthetic catalog and tier objects for the persistence tests. */
public final class CatalogFixtures {

  private CatalogFixtures() {}

  /** An amount in euro. */
  public static Money eur(String amount) {
    return Money.of(amount, Currency.EUR);
  }

  /** A root, zero-rated category. */
  public static Category rootCategory() {
    return new Category(
        new CategoryId("cat-bars"), "Bars", Optional.empty(), TaxCategory.INVESTMENT_GRADE);
  }

  /** A standard-rated category under the root. */
  public static Category childCategory() {
    return new Category(
        new CategoryId("cat-silver"),
        "Silver",
        Optional.of(new CategoryId("cat-bars")),
        TaxCategory.STANDARD);
  }

  /** A fixed-price gold product in the given category. */
  public static Product fixedPriceProduct(String id, Category category) {
    return new Product(
        new ProductId(id),
        "Gold bar 1 oz",
        new ProductSpecification(
            Metal.GOLD,
            Purity.of("999.9"),
            Weight.of("1", WeightUnit.TROY_OUNCE),
            Optional.of("40 x 28 mm")),
        category,
        StockStatus.IN_STOCK,
        Optional.of(eur("1959.30")));
  }

  /** A priced-on-request platinum product in the given category. */
  public static Product onRequestProduct(String id, Category category) {
    return new Product(
        new ProductId(id),
        "Platinum coin",
        new ProductSpecification(
            Metal.PLATINUM,
            Purity.of("950"),
            Weight.of("31.10", WeightUnit.GRAM),
            Optional.empty()),
        category,
        StockStatus.ON_REQUEST,
        Optional.empty());
  }

  /** A fulfillment tier for a region. */
  public static FulfillmentTier tier(String id, String region) {
    return new FulfillmentTier(
        new FulfillmentTierId(id),
        new Region(region),
        eur("5000.00"),
        Weight.of("2.5", WeightUnit.KILOGRAM),
        eur("12.50"),
        new TransitTime(2, 4));
  }
}
