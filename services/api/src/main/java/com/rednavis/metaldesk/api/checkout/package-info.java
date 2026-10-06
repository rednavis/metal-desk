/**
 * Checkout (BRD sections 7.4 to 7.9). This package holds the session that carries a checkout from
 * step to step, and step 1: customer and delivery data.
 *
 * <p><strong>A checkout session is server-side state, and that does not contradict Architecture
 * section 5.</strong> That section's statelessness is about <em>authentication</em>: there is no
 * server-side login session and no shared session store for auth, so the service scales
 * horizontally on a signed token alone. A multi-step checkout is different: it has real
 * intermediate state, and BRD FR-7.1 requires that the customer can go back and edit any earlier
 * step. That state lives in the database (a {@code CheckoutSession}, found by an opaque id the
 * client holds), not in memory and not in the token, so any instance can serve any request, and
 * nothing about the customer's basket or address rides in the JWT.
 *
 * <p>A session holds personal data for a checkout that may never finish, so it expires.
 */
package com.rednavis.metaldesk.api.checkout;
