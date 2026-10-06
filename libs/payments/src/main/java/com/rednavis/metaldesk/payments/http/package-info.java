/**
 * The transport every provider adapter shares: {@link
 * com.rednavis.metaldesk.payments.http.JsonHttpClient}, a small non-blocking JSON-over-HTTP client
 * that turns every transport failure into a {@code PaymentProviderException}, and {@link
 * com.rednavis.metaldesk.payments.http.HttpEndpoint}, the validated address, timeout and retry
 * budget an adapter is configured with.
 *
 * <p>It exists so that each adapter (gateway, wallet, and any future one) has the same client
 * style, and a fix to timeouts or retries is made once. It knows nothing about any provider's wire
 * types or what an answer means. Taking a payment must never be retried; only calls that read state
 * may be.
 */
package com.rednavis.metaldesk.payments.http;
