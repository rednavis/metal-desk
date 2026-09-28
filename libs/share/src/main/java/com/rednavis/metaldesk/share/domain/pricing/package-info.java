/**
 * Price derivation and tax (Architecture section 3): the one place where a number the business
 * controls, the {@link com.rednavis.metaldesk.share.domain.pricing.Margin}, meets one it does not,
 * the {@link com.rednavis.metaldesk.share.domain.pricing.ReferencePrice} of a metal.
 *
 * <p><strong>Margin (BRD BR-3).</strong> {@link
 * com.rednavis.metaldesk.share.domain.pricing.PriceDerivation#derive} computes the sellable unit
 * price as {@code spot + spot x margin}. <em>The purity and weight basis is this package's reading,
 * not the BRD's:</em> the reference price is per gram of pure metal, so spot for a product is the
 * value of its <em>fine weight</em>, its weight in grams times its fineness over 1000. A product
 * priced without that factor would be wrong by exactly the fineness ratio. The pricing bridge
 * (T-039) must use this same formula and not reinterpret it.
 *
 * <p><strong>Tax (BRD BR-4).</strong> {@link com.rednavis.metaldesk.share.domain.pricing.TaxRate}
 * resolves a {@code TaxCategory} to a rate: zero for investment-grade products, and for everything
 * else a configured value looked up by reference, never a compiled-in constant.
 *
 * <p><strong>Price finality (BRD BR-2).</strong> The price that settles an order is the price at
 * ordering time, so an order line snapshots the whole {@link
 * com.rednavis.metaldesk.share.domain.pricing.SellablePrice}, including the rule and reference
 * price behind it (T-014). Nothing here reads a clock; the caller supplies the observation instant,
 * which keeps every derivation deterministic. Nothing here uses binary floating point, and rounding
 * happens once, at the end, in {@link com.rednavis.metaldesk.share.domain.money.Money}.
 */
package com.rednavis.metaldesk.share.domain.pricing;
