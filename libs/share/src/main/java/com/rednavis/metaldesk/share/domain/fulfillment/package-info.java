/**
 * Delivery tiering and quotes (Architecture section 3, BRD section 7.5).
 *
 * <p><strong>A fulfillment tier is data, not code</strong> (Architecture section 3): staff
 * configure a {@link com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier} per region
 * with a value ceiling, a weight ceiling, a delivery price and a transit time, and {@link
 * com.rednavis.metaldesk.share.domain.fulfillment.TierSelector} evaluates an order against whatever
 * is configured. Inside both ceilings the order is priced automatically (BRD FR-5.2); over either,
 * it goes to manager handoff (FR-5.3, BR-10), and a {@link
 * com.rednavis.metaldesk.share.domain.fulfillment.ManagerQuote} carries what staff then set.
 *
 * <p>Evaluation uses the order value <em>before tax</em> (BRD FR-5.1, BR-8), never the gross total.
 * A region with no tier is its own outcome, never a zero-cost quote.
 *
 * <p>A {@link com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote} carries one cost:
 * insurance is folded into delivery cost and is never a separate line (BR-7, FR-5.4).
 */
package com.rednavis.metaldesk.share.domain.fulfillment;
