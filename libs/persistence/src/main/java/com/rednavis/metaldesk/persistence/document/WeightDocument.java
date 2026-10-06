package com.rednavis.metaldesk.persistence.document;

import com.rednavis.metaldesk.share.domain.measure.WeightUnit;

/**
 * A weight as stored.
 *
 * @param amount the decimal amount in the given unit
 * @param unit the unit the weight was entered in, kept so it reads back as entered
 */
public record WeightDocument(String amount, WeightUnit unit) {}
