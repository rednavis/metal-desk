package com.rednavis.metaldesk.payments.gateway;

import com.rednavis.metaldesk.payments.http.JsonHttpClient;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * The gateway's client: the two calls the gateway understands, over the shared non-blocking {@link
 * JsonHttpClient}. It knows the gateway's paths and wire types and nothing about what an answer
 * means; {@link GatewayProvider} decides that.
 *
 * <p><strong>Retries.</strong> {@link #authorise} is never retried: once the request may have
 * reached the gateway, a timeout does not mean the payment failed, and repeating it could take the
 * money twice. {@link #confirm} only asks the gateway for the state of a payment it already knows,
 * so it is retried on a transport failure, up to the configured budget, and never on a malformed
 * answer.
 *
 * <p><strong>Stub.</strong> When the configuration says {@code stub}, neither call goes out: both
 * answer {@code captured} with a made-up reference, for development and demonstration.
 */
final class GatewayClient {

  private final JsonHttpClient http;
  private final boolean stub;

  /**
   * Creates a client for a gateway.
   *
   * @param configuration where the gateway is and how patient to be
   */
  /* default */ GatewayClient(GatewayConfiguration configuration) {
    this.http = new JsonHttpClient(configuration.endpoint());
    this.stub = configuration.stub();
  }

  /**
   * Asks the gateway to take a payment. Never retried.
   *
   * @param request what to take
   * @return the gateway's answer, or an error of type {@link PaymentProviderException}
   */
  /* default */ Mono<GatewayResponse> authorise(GatewayRequest request) {
    return stub
        ? Mono.fromSupplier(GatewayClient::captured)
        : http.post("/v1/payments", request, GatewayResponse.class);
  }

  /**
   * Asks the gateway for the final state of a payment it already knows. Retried on a transport
   * failure, up to the configured budget.
   *
   * @param reference the gateway's handle for the payment
   * @return the gateway's answer, or an error of type {@link PaymentProviderException}
   */
  /* default */ Mono<GatewayResponse> confirm(String reference) {
    return stub
        ? Mono.fromSupplier(GatewayClient::captured)
        : http.postIdempotent(
            "/v1/payments/" + reference + "/confirm", Map.of(), GatewayResponse.class);
  }

  /** The canned answer of a stubbed gateway: the payment was taken. */
  private static GatewayResponse captured() {
    return new GatewayResponse("captured", "stub-" + UUID.randomUUID(), null, null);
  }
}
