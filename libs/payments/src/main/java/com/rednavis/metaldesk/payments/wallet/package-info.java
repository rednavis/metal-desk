/**
 * The wallet adapter (Architecture section 4): {@link
 * com.rednavis.metaldesk.payments.wallet.WalletProvider} implements {@code PaymentProvider} for the
 * separate, account-based wallet, on the same non-blocking client as the gateway adapter.
 *
 * <p>It is mocked at the boundary like every other external dependency (ADR-0002): the provider's
 * address is {@link com.rednavis.metaldesk.payments.wallet.WalletConfiguration}, supplied from
 * outside, and in this reference build WireMock answers for it. Nothing in this package names a
 * host or holds a credential. A timeout is never a decline, and taking a payment is never retried.
 */
package com.rednavis.metaldesk.payments.wallet;
