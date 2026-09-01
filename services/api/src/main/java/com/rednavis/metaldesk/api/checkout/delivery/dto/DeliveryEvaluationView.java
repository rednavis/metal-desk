package com.rednavis.metaldesk.api.checkout.delivery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.api.catalog.dto.PriceView;

/**
 * The result of evaluating a checkout's delivery (BRD FR-5.2, FR-5.3): either an automatic quote
 * with payment allowed, or a reason a manager is needed, with which ceiling bound.
 *
 * <p>The client shows the "Pay" action for {@code PAYMENT_ALLOWED} and the manager-handoff action
 * for {@code HANDOFF_REQUIRED}, and explains it from {@code reason}; the server refuses payment on
 * a handoff session regardless of what the client offers.
 *
 * @param stage {@code PAYMENT_ALLOWED} or {@code HANDOFF_REQUIRED}
 * @param quote the automatic quote; present only when payment is allowed
 * @param reason {@code VALUE_CEILING_EXCEEDED}, {@code WEIGHT_CEILING_EXCEEDED} or {@code
 *     NO_TIER_FOR_REGION}; present only when a manager is needed
 * @param boundCeiling {@code VALUE} or {@code WEIGHT} when a ceiling bound; absent otherwise
 * @param exTaxValue the order value before tax, which the tiers were evaluated on
 * @param weightGrams the total weight in grams, as decimal text
 * @param handoffReference the reference of the handoff, once the order was handed to staff
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryEvaluationView(
    String stage,
    QuoteView quote,
    String reason,
    String boundCeiling,
    PriceView exTaxValue,
    String weightGrams,
    String handoffReference) {}
