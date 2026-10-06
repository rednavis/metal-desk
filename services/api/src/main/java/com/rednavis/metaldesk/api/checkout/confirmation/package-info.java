/**
 * Checkout step 5 (BRD FR-8.1, FR-6.2, task T-038): the confirmation and the notifications that
 * close an order, on every success path.
 *
 * <p>Mails are recorded per order and template, so they are idempotent. The invoice documents are
 * read from the archive filled during payment and attached as they are; nothing in this package
 * renders an invoice.
 */
package com.rednavis.metaldesk.api.checkout.confirmation;
