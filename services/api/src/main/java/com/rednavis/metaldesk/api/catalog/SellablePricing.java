package com.rednavis.metaldesk.api.catalog;

import com.rednavis.metaldesk.api.marketdata.ReferencePriceCache;
import com.rednavis.metaldesk.api.persistence.document.PriceDocument.ScopeKind;
import com.rednavis.metaldesk.api.persistence.document.PriceRuleDocument;
import com.rednavis.metaldesk.api.persistence.mapper.PriceRuleMapper;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.pricing.PriceDerivation;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides whether a product has a sellable price right now, and what it is.
 *
 * <p>The price is always {@link PriceDerivation#derive} (BRD BR-3); this class only gathers its
 * inputs, so there is one formula. A product is priced when all of these hold, and is otherwise
 * {@code ON_REQUEST} — the price-inquiry path of FR-9.1, not an error:
 *
 * <ol>
 *   <li>the catalog holds a price for it (a product without one is sold on request, FR-1.2);
 *   <li>a margin rule applies — the product's own rule if it has one, else its category's (the more
 *       specific rule wins; the BRD is silent, and T-013 left it to the rule store);
 *   <li>a reference price for its metal has been observed. Until the first poll returns, products
 *       are on request rather than shown at a made-up price.
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class SellablePricing {

  private final ReferencePriceCache cache;
  private final PriceRuleMapper ruleMapper;

  /**
   * Derives the current sellable price of a product, if it has one.
   *
   * @param product the product
   * @param rules the stored rules that may apply, keyed by {@link PriceRuleDocument#id()}
   * @return the sellable price, or empty if the product is on request
   */
  public Optional<SellablePrice> sellable(Product product, Map<String, PriceRuleDocument> rules) {
    return product.price().isEmpty()
        ? Optional.empty()
        : ruleFor(product, rules)
            .flatMap(
                stored ->
                    cache
                        .find(product.specification().metal())
                        .map(
                            observation ->
                                PriceDerivation.derive(
                                    observation.latest(),
                                    product.specification(),
                                    ruleMapper.toDomain(stored))));
  }

  private static Optional<PriceRuleDocument> ruleFor(
      Product product, Map<String, PriceRuleDocument> rules) {
    final String productRule = PriceRuleDocument.idFor(ScopeKind.PRODUCT, product.id().value());
    final String categoryRule =
        PriceRuleDocument.idFor(ScopeKind.CATEGORY, product.category().id().value());
    return Optional.ofNullable(rules.get(productRule))
        .or(() -> Optional.ofNullable(rules.get(categoryRule)));
  }
}
