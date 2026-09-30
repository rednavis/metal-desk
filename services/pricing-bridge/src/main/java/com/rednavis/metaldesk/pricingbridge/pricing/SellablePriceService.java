package com.rednavis.metaldesk.pricingbridge.pricing;

import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.pricingbridge.subscription.ReferencePriceBook;
import com.rednavis.metaldesk.share.domain.catalog.ProductSpecification;
import com.rednavis.metaldesk.share.domain.pricing.PriceDerivation;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Turns reference prices into sellable prices (BRD BR-3) by calling {@link PriceDerivation}.
 *
 * <p>This class does no price arithmetic of its own, and must not: the purity basis BR-3 leaves
 * open is settled once, in {@code PriceDerivation}, and a second formula here would let this
 * service and the catalog quote different prices for the same product. A test asserts the results
 * equal a direct {@code PriceDerivation} call.
 *
 * <p>Which rule applies to a product is the caller's decision; the bridge holds no catalog.
 */
@Service
@RequiredArgsConstructor
public class SellablePriceService {

  private final ReferencePriceBook book;

  /**
   * Derives the sellable price a tick implies for a product.
   *
   * @param tick the tick, for the product's metal
   * @param spec the product's specification
   * @param rule the margin rule to apply
   * @return the sellable price
   */
  public SellablePrice derive(ReferencePriceTick tick, ProductSpecification spec, PriceRule rule) {
    return PriceDerivation.derive(tick.toReferencePrice(), spec, rule);
  }

  /**
   * Derives the sellable price from the latest observed price of the product's metal.
   *
   * @param spec the product's specification
   * @param rule the margin rule to apply
   * @return the sellable price, or empty if no price of that metal has been observed yet
   */
  public Optional<SellablePrice> quote(ProductSpecification spec, PriceRule rule) {
    return book.find(spec.metal())
        .map(observation -> PriceDerivation.derive(observation.latest(), spec, rule));
  }
}
