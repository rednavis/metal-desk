package com.rednavis.metaldesk.api.catalog;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import com.rednavis.metaldesk.api.catalog.dto.ProductDetailView;
import com.rednavis.metaldesk.api.catalog.dto.ProductSummaryView;
import com.rednavis.metaldesk.api.catalog.dto.TaxTreatmentView;
import com.rednavis.metaldesk.api.catalog.dto.WeightView;
import com.rednavis.metaldesk.api.persistence.repository.CategoryRepository;
import com.rednavis.metaldesk.api.persistence.repository.PriceRuleRepository;
import com.rednavis.metaldesk.persistence.document.CategoryDocument;
import com.rednavis.metaldesk.persistence.document.PriceDocument.ScopeKind;
import com.rednavis.metaldesk.persistence.document.PriceRuleDocument;
import com.rednavis.metaldesk.persistence.document.ProductDocument;
import com.rednavis.metaldesk.persistence.mapper.CategoryMapper;
import com.rednavis.metaldesk.persistence.mapper.ProductMapper;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.PricingMode;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import com.rednavis.metaldesk.share.domain.pricing.TaxRate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Turns stored products into the views the storefront gets.
 *
 * <p>A list of products is assembled in one pass: the categories and price rules they need are
 * fetched once for the whole list, not once per product. The domain product is rebuilt (so its
 * invariants are re-checked), the price is derived by {@link SellablePricing}, and only then is a
 * view built. No domain type leaves this class.
 */
@Component
@RequiredArgsConstructor
public class ProductViewAssembler {

  private final CategoryRepository categoryRepo;
  private final PriceRuleRepository ruleRepo;
  private final CategoryMapper categoryMapper;
  private final ProductMapper productMapper;
  private final SellablePricing pricing;

  /**
   * Fetches what the given products need to be shown: their categories and applicable rules.
   *
   * @param documents the stored products
   * @return the lookup, to hand to {@link #summary} and {@link #detail}
   */
  public Mono<Context> context(List<ProductDocument> documents) {
    final List<String> categoryIds =
        documents.stream().map(ProductDocument::categoryId).distinct().toList();
    final List<String> ruleIds =
        documents.stream()
            .flatMap(
                document ->
                    Stream.of(
                        PriceRuleDocument.idFor(ScopeKind.PRODUCT, document.id()),
                        PriceRuleDocument.idFor(ScopeKind.CATEGORY, document.categoryId())))
            .distinct()
            .toList();
    return Mono.zip(
        categoryRepo
            .findAllById(categoryIds)
            .collectMap(CategoryDocument::id, categoryMapper::toDomain),
        ruleRepo.findAllById(ruleIds).collectMap(PriceRuleDocument::id, Function.identity()),
        Context::new);
  }

  /**
   * Builds the listing entry of a product.
   *
   * @param document the stored product
   * @param context what {@link #context} fetched
   * @return the entry, with a price only if the product has a sellable one
   */
  public ProductSummaryView summary(ProductDocument document, Context context) {
    final Product product = domainProduct(document, context);
    final Optional<SellablePrice> price = pricing.sellable(product, context.rules());
    return new ProductSummaryView(
        document.id(),
        product.name(),
        document.categoryId(),
        product.specification().metal(),
        product.stock(),
        mode(price),
        price.map(ProductViewAssembler::priceView).orElse(null));
  }

  /**
   * Builds the detail page of a product (BRD FR-1.3).
   *
   * @param document the stored product
   * @param context what {@link #context} fetched
   * @return the detail
   */
  public ProductDetailView detail(ProductDocument document, Context context) {
    final Product product = domainProduct(document, context);
    final Optional<SellablePrice> price = pricing.sellable(product, context.rules());
    return new ProductDetailView(
        document.id(),
        product.name(),
        document.categoryId(),
        product.category().name(),
        product.specification().metal(),
        product.specification().purity().partsPerThousand().toPlainString(),
        new WeightView(
            product.specification().weight().amount().toPlainString(),
            product.specification().weight().unit()),
        product.specification().dimensions().orElse(null),
        product.stock(),
        mode(price),
        price.map(ProductViewAssembler::priceView).orElse(null),
        new TaxTreatmentView(
            product.category().taxCategory(),
            TaxRate.forCategory(product.category().taxCategory()).percent().toPlainString()));
  }

  private Product domainProduct(ProductDocument document, Context context) {
    final Category category = context.categories().get(document.categoryId());
    if (category == null) {
      throw new IllegalStateException(
          "Product " + document.id() + " refers to missing category " + document.categoryId());
    }
    return productMapper.toDomain(document, category);
  }

  private static PricingMode mode(Optional<SellablePrice> price) {
    return price.isPresent() ? PricingMode.FIXED : PricingMode.ON_REQUEST;
  }

  private static PriceView priceView(SellablePrice price) {
    final Money unit = price.unitPrice();
    return new PriceView(unit.amount().toPlainString(), unit.currency().code());
  }

  /**
   * The categories and price rules the products of one response need.
   *
   * @param categories the categories by id
   * @param rules the stored price rules by their scope-derived id
   */
  public record Context(Map<String, Category> categories, Map<String, PriceRuleDocument> rules) {

    /** Copies the maps, so a context cannot be changed through them. */
    public Context {
      categories = Map.copyOf(categories);
      rules = Map.copyOf(rules);
    }
  }
}
