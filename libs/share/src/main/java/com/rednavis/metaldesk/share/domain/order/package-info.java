/**
 * The order side of the domain model (Architecture section 3): an {@link
 * com.rednavis.metaldesk.share.domain.order.Order} of {@link
 * com.rednavis.metaldesk.share.domain.order.OrderLine}s, its totals, and its number.
 *
 * <p><strong>The snapshot rule.</strong> Architecture section 3 names it the first of three
 * modeling decisions that carry the rest of the design: <em>"OrderLine snapshots price and tax
 * category at order time. A later change to a product's margin or tax rule must never retroactively
 * change a historical order's total."</em> This is what BRD BR-2 (price finality) means at the
 * data-model level. An order line therefore holds the whole {@link
 * com.rednavis.metaldesk.share.domain.pricing.SellablePrice}, the resolved tax classification and
 * the computed tax, and holds no reference to the catalog entry, so nothing about an order is
 * resolved on read.
 *
 * <p><strong>Totals (BRD BR-5).</strong> {@link
 * com.rednavis.metaldesk.share.domain.order.OrderTotalsCalculator} computes {@code order_total =
 * sum(unit_price x quantity) + tax + delivery_cost} from the line snapshots. Delivery is one
 * amount, insurance folded in (BR-7).
 *
 * <p><strong>Numbering (BRD BR-6).</strong> An {@link
 * com.rednavis.metaldesk.share.domain.order.OrderNumber} is {@code ddMMyyyy} plus a four-digit
 * daily sequence, for example {@code 080220220004}. It represents and validates a number;
 * allocating the sequence needs a transactional counter and belongs to persistence (T-030).
 *
 * <p><strong>Quantity (BRD FR-3.3).</strong> A line holds one to ten units of a product; the cap is
 * the BRD's illustrative figure.
 *
 * <p><strong>Status (Architecture section 6).</strong> {@link
 * com.rednavis.metaldesk.share.domain.order.OrderStatus} lists the eight states, and {@link
 * com.rednavis.metaldesk.share.domain.order.OrderStateMachine} is the one table of which {@link
 * com.rednavis.metaldesk.share.domain.order.TransitionTrigger} moves an order from which status to
 * which. {@code AWAITING_MANAGER_QUOTE} is a state, not a flag, so order history shows a consistent
 * status whichever path an order took. {@code Order} itself has no method that changes its status.
 */
package com.rednavis.metaldesk.share.domain.order;
