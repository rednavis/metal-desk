package com.rednavis.metaldesk.api.checkout.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.api.cart.dto.CartView;
import com.rednavis.metaldesk.api.checkout.delivery.dto.DeliveryEvaluationView;

/**
 * A checkout session as the client sees it.
 *
 * @param checkoutId the session's id
 * @param source {@code CART} or {@code BUY_NOW}
 * @param basket the basket lines with prices as quoted when the session started
 * @param details the step-1 data; absent before step 1
 * @param consent the privacy acceptance; absent before step 1
 * @param conversion the guest's quick registration; absent unless asked for
 * @param delivery the last delivery evaluation; absent before one
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionView(
    String checkoutId,
    String source,
    CartView basket,
    DetailsView details,
    ConsentView consent,
    ConversionView conversion,
    DeliveryEvaluationView delivery) {}
