/**
 * Paying by invoice (BRD FR-6.2, Architecture section 4): {@link
 * com.rednavis.metaldesk.payments.invoice.InvoiceProvider} implements {@code PaymentProvider} for
 * the third provider family, the one that <strong>makes no outbound call</strong>. It renders the
 * order's invoice and reports {@code DocumentIssued}; an invoice is a promise to pay, so its status
 * is pending, never captured.
 *
 * <p><strong>One document or two.</strong> {@link
 * com.rednavis.metaldesk.payments.invoice.InvoiceSplitter} groups an order's lines by the tax
 * category snapshotted on each line and gives one document scope, or two when the cart mixes
 * tax-exempt and taxable categories, and never more. The cap of two is tied to {@code TaxCategory}
 * having exactly two members; a third would mean revisiting this rule and FR-6.2.
 *
 * <p><strong>Ports.</strong> The provider reads the order through {@link
 * com.rednavis.metaldesk.payments.invoice.InvoiceOrders}, draws documents through {@link
 * com.rednavis.metaldesk.payments.invoice.InvoiceRenderer} and hands them to {@link
 * com.rednavis.metaldesk.payments.invoice.InvoiceSink}. Sending the emails is not done here.
 *
 * <p><strong>Locale is an input.</strong> Documents are rendered in the locale of the payment
 * request, in at least English and German; nothing here reads a default locale.
 */
package com.rednavis.metaldesk.payments.invoice;
