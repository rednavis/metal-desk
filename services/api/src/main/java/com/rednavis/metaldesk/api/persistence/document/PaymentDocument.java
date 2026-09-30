package com.rednavis.metaldesk.api.persistence.document;

import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentStatus;

/**
 * A payment record as stored, embedded in an order.
 *
 * @param providerId the id of the payment provider that handled it
 * @param method the method the customer chose
 * @param status where the payment stands
 * @param reference the provider's reference
 * @param amount the amount paid or due
 */
public record PaymentDocument(
    String providerId,
    PaymentMethod method,
    PaymentStatus status,
    String reference,
    MoneyDocument amount) {}
