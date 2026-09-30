/**
 * The cart (BRD section 7.3): an idempotent add, buy-now, an editable capped line list, an explicit
 * empty state, and a cart that survives sign-out.
 *
 * <p><strong>The cart is not a domain aggregate.</strong> It is a transient, pre-order artefact;
 * {@code Order} is the aggregate. It lives here, in {@code services/api}, and not in {@code
 * libs/share}, where it would be a shared type only one service uses.
 *
 * <p><strong>A cart holds products and quantities, never prices.</strong> Unit price, line tax and
 * the running total are derived on every read through the same pricing as the catalog (BRD BR-3),
 * so a cart cannot show a stale price after the market moves. Price finality (BR-2) applies at
 * order time, where the order line snapshots the price.
 *
 * <p><strong>A cart's identity outlives authentication.</strong> A cart is found by an opaque
 * reference held in a cookie, which works as a capability: whoever holds it can read the cart, so a
 * cart stays reachable after sign-out (BRD FR-2.7). See {@link
 * com.rednavis.metaldesk.api.cart.CartResolver}.
 */
package com.rednavis.metaldesk.api.cart;
