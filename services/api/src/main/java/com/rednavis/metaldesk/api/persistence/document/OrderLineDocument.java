package com.rednavis.metaldesk.api.persistence.document;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;

/**
 * One order line as stored: a full snapshot of what was sold and at what price, with no reference
 * back into the catalog (BRD BR-2).
 *
 * @param productId the id of the product sold
 * @param productName the product's name when it was sold
 * @param quantity the number of units
 * @param price the sellable price snapshot
 * @param taxCategory the tax category the line was classified under when it was sold
 * @param tax the tax computed on the line
 */
public record OrderLineDocument(
    String productId,
    String productName,
    int quantity,
    PriceDocument price,
    TaxCategory taxCategory,
    TaxDocument tax) {}
