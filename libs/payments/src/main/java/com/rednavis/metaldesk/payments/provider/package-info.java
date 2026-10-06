/**
 * The {@code PaymentProvider} abstraction (Architecture section 4): the single interface checkout
 * calls, and the request and result types it exchanges. The adapters that implement it (gateway,
 * wallet, invoice) come later (T-018, T-019).
 *
 * <p>Three things are deliberate here:
 *
 * <ul>
 *   <li><strong>Nothing vendor-shaped.</strong> No HTTP types, no provider error strings, no vendor
 *       names; an adapter translates its vendor into these types.
 *   <li><strong>A decline is a value, a transport failure is an error.</strong> {@link
 *       com.rednavis.metaldesk.payments.provider.PaymentOutcome.Declined} is an outcome the
 *       customer recovers from (BRD FR-6.3); {@link
 *       com.rednavis.metaldesk.payments.provider.PaymentProviderException} means the provider could
 *       not be reached or understood, and is an incident.
 *   <li><strong>No instrument data.</strong> A {@link
 *       com.rednavis.metaldesk.payments.provider.PaymentIntent} holds references and an amount,
 *       with no bag of extra fields, so it cannot be used to carry what the platform must never
 *       store.
 * </ul>
 *
 * <p>Every operation is reactive; nothing in this package blocks.
 */
package com.rednavis.metaldesk.payments.provider;
