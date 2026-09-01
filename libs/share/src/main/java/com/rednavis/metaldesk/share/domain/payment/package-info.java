/**
 * Payments as data (Architecture sections 3 and 4): a {@link
 * com.rednavis.metaldesk.share.domain.payment.PaymentRecord} of an order's payment, made by a
 * {@link com.rednavis.metaldesk.share.domain.payment.PaymentMethod} that belongs to one {@link
 * com.rednavis.metaldesk.share.domain.payment.PaymentMethodGroup}.
 *
 * <p><strong>No instrument data, by construction.</strong> The platform never stores raw card data,
 * so the model cannot hold it: a payment is described by a provider, a method, a status, an opaque
 * {@link com.rednavis.metaldesk.share.domain.payment.ProviderReference} and an amount, and nothing
 * else. There is deliberately no generic bag of extra fields and no free-form details. A test
 * asserts this, and it should not be weakened.
 *
 * <p><strong>The group is what rules key on</strong> (BRD BR-9): gateway-processed methods are
 * restricted above a configured order-value ceiling, and invoice is then the only path. The ceiling
 * is configuration; it is not in this package. The provider interface and its adapters are T-017 to
 * T-019.
 */
package com.rednavis.metaldesk.share.domain.payment;
