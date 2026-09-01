package com.rednavis.metaldesk.api.catalog.dto;

import com.rednavis.metaldesk.share.domain.measure.WeightUnit;

/**
 * A weight.
 *
 * @param amount the amount as decimal text
 * @param unit the unit it is given in
 */
public record WeightView(String amount, WeightUnit unit) {}
