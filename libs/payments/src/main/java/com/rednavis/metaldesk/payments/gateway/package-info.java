/**
 * The gateway adapter (Architecture section 4): {@link
 * com.rednavis.metaldesk.payments.gateway.GatewayProvider} implements {@code PaymentProvider} for
 * card, bank debit, bank redirect, bank transfer and saved wallet, over a small non-blocking HTTP
 * client.
 *
 * <p><strong>Every call is mocked at the boundary</strong> (ADR-0002): the gateway's address is
 * {@link com.rednavis.metaldesk.payments.gateway.GatewayConfiguration}, supplied from outside, and
 * in this reference build WireMock answers for it, including the decline and timeout paths. Nothing
 * in this package names a host or holds a credential.
 *
 * <p><strong>A timeout is not a decline.</strong> A gateway that cannot be reached, does not answer
 * in time or answers with something unintelligible ends the call with a {@code
 * PaymentProviderException}; only the gateway actually saying no is a {@code Declined} outcome.
 *
 * <p><strong>Retries are deliberate.</strong> An authorisation is never retried, because a timeout
 * does not prove it failed and a duplicate charge is worse than a failed checkout. A confirmation
 * only reads a payment's state, so it is retried on a transport failure, up to a small configured
 * budget.
 *
 * <p>The wire types are package-private on purpose: callers see only {@code PaymentProvider}.
 */
package com.rednavis.metaldesk.payments.gateway;
