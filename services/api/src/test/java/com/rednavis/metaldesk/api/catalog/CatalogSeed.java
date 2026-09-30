package com.rednavis.metaldesk.api.catalog;

import com.rednavis.metaldesk.api.marketdata.ReferencePriceCache;
import com.rednavis.metaldesk.api.persistence.CatalogFixtures;
import com.rednavis.metaldesk.api.persistence.mapper.CategoryMapper;
import com.rednavis.metaldesk.api.persistence.mapper.PriceRuleMapper;
import com.rednavis.metaldesk.api.persistence.mapper.ProductMapper;
import com.rednavis.metaldesk.api.persistence.repository.CategoryRepository;
import com.rednavis.metaldesk.api.persistence.repository.PriceRuleRepository;
import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.Margin;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Writes small synthetic catalog data, prices and rules for the catalog API tests. */
@Component
public class CatalogSeed {

  @Autowired private CategoryRepository categories;
  @Autowired private ProductRepository products;
  @Autowired private PriceRuleRepository rules;
  @Autowired private CategoryMapper categoryMapper;
  @Autowired private ProductMapper productMapper;
  @Autowired private PriceRuleMapper ruleMapper;
  @Autowired private ReferencePriceCache cache;

  /** Stores a root category. */
  public Category category(String id, TaxCategory tax) {
    final Category category =
        new Category(new CategoryId(id), "Category " + id, Optional.empty(), tax);
    categories.save(categoryMapper.toDocument(category)).block();
    return category;
  }

  /** Stores a product priced at a nominal amount. */
  public Product pricedProduct(String id, String name, Category category) {
    return product(id, name, category, Optional.of(Money.of("1.00", Currency.EUR)));
  }

  /** Stores a priced product of the given weight in grams. */
  public Product pricedProduct(String id, String name, Category category, String grams) {
    final Product product =
        CatalogFixtures.weighedProduct(
            id, name, category, Optional.of(Money.of("1.00", Currency.EUR)), grams);
    products.save(productMapper.toDocument(product)).block();
    return product;
  }

  /** Stores a product with no catalog price. */
  public Product unpricedProduct(String id, String name, Category category) {
    return product(id, name, category, Optional.empty());
  }

  private Product product(String id, String name, Category category, Optional<Money> price) {
    final Product product = CatalogFixtures.namedProduct(id, name, category, price);
    products.save(productMapper.toDocument(product)).block();
    return product;
  }

  /** Stores a margin rule for a whole category. */
  public PriceRule marginForCategory(Category category, String percent) {
    final PriceRule rule =
        new PriceRule(Margin.of(percent), new PriceRule.Scope.ForCategory(category.id()));
    rules.save(ruleMapper.toDocument(rule)).block();
    return rule;
  }

  /** Observes a gold price now, so a later observation always replaces an earlier one. */
  public ReferencePrice observeGold(String pricePerGram) {
    final ReferencePrice price =
        new ReferencePrice(Metal.GOLD, Money.of(pricePerGram, Currency.EUR), Instant.now());
    cache.record(price);
    return price;
  }
}
