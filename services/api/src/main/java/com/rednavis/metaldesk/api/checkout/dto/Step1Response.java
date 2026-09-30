package com.rednavis.metaldesk.api.checkout.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The answer to a successful step 1.
 *
 * @param checkoutId the checkout session's id
 * @param step1Complete always true; a failure is an error response
 * @param details the stored customer and delivery data
 * @param consent the recorded privacy acceptance
 * @param conversion the guest's quick registration; absent unless one was asked for
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Step1Response(
    String checkoutId,
    boolean step1Complete,
    DetailsView details,
    ConsentView consent,
    ConversionView conversion) {}
