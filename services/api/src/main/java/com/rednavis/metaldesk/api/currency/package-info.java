/**
 * Display currency (BRD FR-1.8): the catalog and the cart can be shown in another currency than the
 * one orders are settled in.
 *
 * <p><strong>Conversion happens here, on the server, and only for display.</strong> The client is a
 * formatter: it never multiplies anything, so what it shows is what the server computed. Orders are
 * always priced, taxed and charged in the settlement currency (BRD BR-5, and the payment overview
 * must equal the charged amount), so checkout, payment, confirmation and order history are never
 * converted and say which currency they are in.
 *
 * <p><strong>The rates are fake.</strong> No rate source exists in this build (ADR-0002, and T-013
 * leaves conversion outside the domain), so {@code FakeExchangeRates} serves rates from
 * configuration, and every response that depends on them says so through {@code rateSource}. They
 * are deliberately not market-like numbers.
 */
package com.rednavis.metaldesk.api.currency;
