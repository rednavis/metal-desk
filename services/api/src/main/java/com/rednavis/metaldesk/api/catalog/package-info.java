/**
 * The read side of the storefront (BRD section 7.1): the category-grouped catalog, product detail,
 * related products and free-text search.
 *
 * <p>Everything here is public and read-only, and none of it serialises a domain type: the wire
 * shapes are the views in {@code dto}. The sellable price is always derived through {@code
 * PriceDerivation} (BRD BR-3), never recomputed here.
 */
package com.rednavis.metaldesk.api.catalog;
