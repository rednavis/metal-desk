package com.rednavis.metaldesk.payments.wallet;

import com.rednavis.metaldesk.payments.http.JsonHttpClient;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * The wallet provider's client: the two calls it understands, over the same non-blocking {@link
 * JsonHttpClient} the gateway uses, so there is one client style. It knows the wallet's paths and
 * wire types and nothing about what an answer means; {@link WalletProvider} decides that.
 *
 * <p>Taking a payment is never retried, because a timeout does not prove it failed and repeating it
 * could charge the wallet twice. Confirming only reads a payment's state, so it is retried on a
 * transport failure up to the configured budget.
 *
 * <p><strong>Stub.</strong> When the configuration says {@code stub}, neither call goes out: both
 * answer {@code captured} with a made-up reference, for development and demonstration.
 */
final class WalletClient {

  private final JsonHttpClient http;
  private final boolean stub;

  /**
   * Creates a client for the wallet provider.
   *
   * @param configuration where the provider is and how patient to be
   */
  /* default */ WalletClient(WalletConfiguration configuration) {
    this.http = new JsonHttpClient(configuration.endpoint());
    this.stub = configuration.stub();
  }

  /**
   * Asks the wallet provider to take a payment. Never retried.
   *
   * @param request what to take
   * @return the provider's answer, or an error of type {@link PaymentProviderException}
   */
  /* default */ Mono<WalletResponse> authorise(WalletRequest request) {
    return stub
        ? Mono.fromSupplier(WalletClient::captured)
        : http.post("/v1/wallet/payments", request, WalletResponse.class);
  }

  /**
   * Asks the wallet provider for the final state of a payment it already knows. Retried on a
   * transport failure, up to the configured budget.
   *
   * @param reference the provider's handle for the payment
   * @return the provider's answer, or an error of type {@link PaymentProviderException}
   */
  /* default */ Mono<WalletResponse> confirm(String reference) {
    return stub
        ? Mono.fromSupplier(WalletClient::captured)
        : http.postIdempotent(
            "/v1/wallet/payments/" + reference + "/confirm", Map.of(), WalletResponse.class);
  }

  /** The canned answer of a stubbed wallet provider: the payment was taken. */
  private static WalletResponse captured() {
    return new WalletResponse("captured", "stub-" + UUID.randomUUID(), null);
  }
}
