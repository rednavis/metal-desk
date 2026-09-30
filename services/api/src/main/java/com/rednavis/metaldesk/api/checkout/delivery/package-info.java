/**
 * Delivery tiering and the manager handoff (BRD section 7.5).
 *
 * <p>The tiers staff configure are <em>data</em> (Architecture section 3): every evaluation reads
 * the current ones, hands them to the pure {@code TierSelector}, and records the outcome on the
 * checkout session as a {@link com.rednavis.metaldesk.api.checkout.delivery.CheckoutStage}. Within
 * both ceilings the order is priced automatically and may be paid for; over either ceiling, or in a
 * region with no tier at all, a human must set the price, and <strong>taking payment is refused by
 * the server</strong> until then ({@link
 * com.rednavis.metaldesk.api.checkout.delivery.PaymentGate}). That refusal is the enforcement of
 * BRD FR-5.3's "does not attempt to collect payment inline"; the client is not a trust boundary.
 */
package com.rednavis.metaldesk.api.checkout.delivery;
