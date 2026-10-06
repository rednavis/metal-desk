package com.rednavis.metaldesk.api.checkout.payment.dto;

/**
 * The checkout a customer continues in to pay an existing order.
 *
 * @param checkoutId the checkout session's id
 */
public record ResumedPaymentView(String checkoutId) {}
