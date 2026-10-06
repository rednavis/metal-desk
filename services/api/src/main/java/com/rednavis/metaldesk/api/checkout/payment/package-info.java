/**
 * Checkout steps 2 to 4 (BRD sections 7.6 and 7.7): the payment methods on offer, the final
 * overview, and executing the payment through the provider abstraction.
 *
 * <p>Nothing here names a payment vendor. A {@link
 * com.rednavis.metaldesk.api.checkout.payment.ProviderRegistry} finds the {@code PaymentProvider}
 * that supports a method, and every payment operation starts by asking the {@link
 * com.rednavis.metaldesk.api.checkout.delivery.PaymentGate} whether the session may be paid for at
 * all. Where the order is created relative to the provider call, and what each provider outcome
 * does, is described on {@link
 * com.rednavis.metaldesk.api.checkout.payment.PaymentExecutionService}.
 */
package com.rednavis.metaldesk.api.checkout.payment;
