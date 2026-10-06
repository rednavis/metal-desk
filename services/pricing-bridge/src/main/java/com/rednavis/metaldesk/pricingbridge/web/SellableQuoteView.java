package com.rednavis.metaldesk.pricingbridge.web;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import java.time.Instant;

/**
 * A derived sellable price with the reference it came from.
 *
 * @param scope the scope of the rule that was applied
 * @param metal the metal
 * @param unitPrice the sellable price of one unit, as decimal text
 * @param currency the currency code
 * @param marginPercent the margin that was applied, as decimal text
 * @param referencePrice the reference price per gram it was derived from
 * @param observedAt when that reference price was observed
 */
public record SellableQuoteView(
    SellableQuoteRequest.Scope scope,
    Metal metal,
    String unitPrice,
    String currency,
    String marginPercent,
    String referencePrice,
    Instant observedAt) {}
