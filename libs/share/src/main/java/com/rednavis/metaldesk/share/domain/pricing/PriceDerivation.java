package com.rednavis.metaldesk.share.domain.pricing;

import com.rednavis.metaldesk.share.domain.catalog.ProductSpecification;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;

/**
 * Turns a live reference price into a sellable unit price, as BRD BR-3 defines it.
 *
 * <p>This is a pure function: no state, no clock, no I/O. The same inputs always give an {@code
 * equals} result, which is what lets the pricing bridge (T-039) call it on every feed tick and the
 * catalog (T-031) on every read and still agree.
 *
 * <p><strong>The fine-weight basis.</strong> BR-3 says only "a live reference price plus a
 * configured margin". A reference price is quoted per gram of <em>pure</em> metal and a product has
 * a gross weight and a fineness, so the derivation is:
 *
 * <pre>
 * fine weight   = weight in grams x (parts per thousand / 1000)
 * metal value   = reference price per gram x fine weight
 * sellable unit = metal value x (1 + margin / 100)
 * </pre>
 *
 * <p>That is BR-3's {@code spot + spot x margin_pct} with spot taken as the value of the product's
 * pure metal. The BRD does not spell the purity basis out; if it is wrong, the BRD needs to say so.
 *
 * <p>Everything is computed in exact decimals and rounded <em>once</em>, at the end, by {@link
 * Money}. Rounding any intermediate step would make results disagree with BR-5 by cents.
 *
 * <p>The rule's scope is not checked: nothing here knows which product the specification belongs
 * to, so choosing the applicable rule is the caller's job. The reference price's metal must match
 * the specification's.
 */
public final class PriceDerivation {

  private PriceDerivation() {}

  /**
   * Derives the sellable unit price of a product.
   *
   * @param reference the live reference price for the product's metal
   * @param spec the product's physical specification
   * @param rule the margin rule to apply
   * @return the sellable unit price, in the reference price's currency, with its provenance
   * @throws ValidationException if an argument is null or the reference price is for a different
   *     metal than the specification's
   */
  public static SellablePrice derive(
      ReferencePrice reference, ProductSpecification spec, PriceRule rule) {
    if (reference == null || spec == null || rule == null) {
      throw new ValidationException(
          "price-derivation.input-missing", "Price derivation requires a reference, spec and rule");
    }
    if (reference.metal() != spec.metal()) {
      throw new ValidationException(
          "price-derivation.metal-mismatch",
          "Reference price is for " + reference.metal() + " but the product is " + spec.metal());
    }
    final BigDecimal fineGrams =
        spec.weight()
            .toCanonical()
            .amount()
            .multiply(spec.purity().partsPerThousand())
            .movePointLeft(3);
    final BigDecimal markup = BigDecimal.ONE.add(rule.margin().asFraction());
    final BigDecimal exact = reference.pricePerGram().amount().multiply(fineGrams).multiply(markup);
    return new SellablePrice(Money.of(exact, reference.pricePerGram().currency()), rule, reference);
  }
}
