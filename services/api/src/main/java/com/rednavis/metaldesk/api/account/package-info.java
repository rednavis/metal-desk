/**
 * The account lifecycle of BRD section 7.2: registration, email verification, password reset and
 * account switching.
 *
 * <p>Verification lives in {@code verification} as a <strong>reusable primitive</strong> (FR-2.6:
 * issue a code, bind it to a user, confirm it) that knows nothing about registration. Registration
 * and password reset are two callers of it, and the checkout task's quick registration (T-035) is a
 * third that needs no change here.
 *
 * <p>Two rules run through every flow. First, a request whose failure could reveal whether an
 * account exists (a reset for an unknown email, a wrong code, an email that is already registered)
 * gets the same answer as one whose success it would otherwise reveal, for the reason FR-2.2 gives
 * for sign-in. Second, a code, a reset link or a password is never logged.
 *
 * <p><strong>Sign-out and the cart (FR-2.7).</strong> Sign-out touches no server-side data: the
 * token is stateless and the client just discards it. The cart is therefore unaffected, and T-034
 * may rely on this contract: <em>a cart belongs to its owner, not to a token, and outlives every
 * token issued for that owner</em>.
 */
package com.rednavis.metaldesk.api.account;
