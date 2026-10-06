/**
 * Sign-in and per-request authentication for {@code services/api} (Architecture section 5).
 *
 * <p>Authentication is a <strong>stateless JWT</strong>: a successful sign-in returns a short-lived
 * signed bearer token, and every later request is authenticated by validating that token alone. No
 * server-side session exists, so the service scales horizontally without a shared session store.
 * The price is that there is no revocation and no refresh token in this phase: a token stays valid
 * until it expires, and signing out only means the client discards it.
 *
 * <p>The password hash runs on a bounded-elastic scheduler, never on the event loop (see {@link
 * com.rednavis.metaldesk.api.auth.PasswordEncoderAdapter}). Failed sign-ins are answered with one
 * response whatever the cause (BRD FR-2.2), and repeated failures from one source are throttled by
 * {@link com.rednavis.metaldesk.api.auth.SignInThrottle}.
 *
 * <p>Registration, verification and password reset are T-033's; {@code apps/admin} does not use
 * this flow.
 */
package com.rednavis.metaldesk.api.auth;
