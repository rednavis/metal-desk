package com.rednavis.metaldesk.api.persistence.document;

/**
 * The tax computed on an order line, as stored.
 *
 * @param net the net amount the tax was computed on
 * @param ratePercent the tax rate in percent, as decimal text
 * @param tax the tax amount
 */
public record TaxDocument(MoneyDocument net, String ratePercent, MoneyDocument tax) {}
