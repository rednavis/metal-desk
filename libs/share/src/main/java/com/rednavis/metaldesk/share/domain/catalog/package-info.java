/**
 * The catalog side of the domain model (Architecture section 3): a {@link
 * com.rednavis.metaldesk.share.domain.catalog.Product} with its {@link
 * com.rednavis.metaldesk.share.domain.catalog.ProductSpecification}, listed under a {@link
 * com.rednavis.metaldesk.share.domain.catalog.Category} that owns the tax classification.
 *
 * <p><strong>Tax is a property of the category, not the product</strong> (BRD BR-4):
 * investment-grade metal products are zero-rated and everything else is standard-rated, decided by
 * {@link com.rednavis.metaldesk.share.domain.catalog.Category#taxCategory()}. The order line
 * snapshots the resolved treatment at order time (T-014), which is what keeps price finality (BR-2)
 * true when a category is reclassified.
 *
 * <p><strong>A product may have no price</strong> (BRD FR-1.2, FR-9.1). Its {@link
 * com.rednavis.metaldesk.share.domain.catalog.PricingMode} is derived from that, never stored.
 *
 * <p><strong>Related-products cap (BRD BR-1).</strong> A product's related products are the others
 * in its category, capped at a fixed count; the BRD's illustrative figure is <em>20</em>. That is a
 * query concern owned by the catalog API (T-031), so the number is recorded here to keep it from
 * being reinvented and is deliberately not enforced or declared as a constant in this module.
 */
package com.rednavis.metaldesk.share.domain.catalog;
