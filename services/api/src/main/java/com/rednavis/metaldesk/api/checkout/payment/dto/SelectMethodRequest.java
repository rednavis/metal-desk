package com.rednavis.metaldesk.api.checkout.payment.dto;

import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;

/**
 * Chooses how to pay (BRD FR-6.1).
 *
 * @param method the method, which must be one on offer
 */
public record SelectMethodRequest(PaymentMethod method) {}
