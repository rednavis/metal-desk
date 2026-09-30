package com.rednavis.metaldesk.api.checkout.delivery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The answer to a manager handoff (BRD FR-5.3): the reference number the customer quotes to
 * support, and why the order needed a manager. No payment was taken.
 *
 * @param reference the reference number, which is the order number
 * @param reason why a manager is needed
 * @param boundCeiling {@code VALUE} or {@code WEIGHT} when a ceiling bound; absent otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HandoffView(String reference, String reason, String boundCeiling) {}
