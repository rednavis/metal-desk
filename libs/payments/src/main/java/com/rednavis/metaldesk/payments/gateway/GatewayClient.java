package com.rednavis.metaldesk.payments.gateway;

import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The thin, non-blocking HTTP client for the gateway. It sends JSON and returns the parsed answer,
 * and turns every way the transport can fail into a {@link PaymentProviderException} of the right
 * {@link PaymentProviderException.Kind}. It knows nothing about what an answer means.
 *
 * <p>Nothing here blocks: each call is the JDK client's asynchronous send, bridged to a {@link
 * Mono}. The timeout is enforced on that {@code Mono}, so it fires whatever the HTTP client does,
 * and cancelling the {@code Mono} cancels the request.
 *
 * <p><strong>Retries.</strong> {@link #authorise} is never retried: once the request may have
 * reached the gateway, a timeout does not mean the payment failed, and repeating it could take the
 * money twice. {@link #confirm} only asks the gateway for the state of a payment it already knows,
 * so it is retried on a transport failure, up to the configured budget, and never on a malformed
 * answer.
 */
final class GatewayClient {

  private static final int HTTP_OK_FIRST = 200;
  private static final int HTTP_OK_LAST = 299;

  private final GatewayConfiguration configuration;
  private final HttpClient http;
  private final JsonMapper mapper;

  /**
   * Creates a client for a gateway.
   *
   * @param configuration where the gateway is and how patient to be
   */
  /* default */ GatewayClient(GatewayConfiguration configuration) {
    this.configuration = configuration;
    this.http = HttpClient.newBuilder().connectTimeout(configuration.timeout()).build();
    this.mapper =
        JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  /**
   * Asks the gateway to take a payment. Never retried.
   *
   * @param request what to take
   * @return the gateway's answer, or an error of type {@link PaymentProviderException}
   */
  /* default */ Mono<GatewayResponse> authorise(GatewayRequest request) {
    return Mono.defer(() -> post("/v1/payments", writeJson(request)));
  }

  /**
   * Asks the gateway for the final state of a payment it already knows. Retried on a transport
   * failure, up to the configured budget.
   *
   * @param reference the gateway's handle for the payment
   * @return the gateway's answer, or an error of type {@link PaymentProviderException}
   */
  /* default */ Mono<GatewayResponse> confirm(String reference) {
    return Mono.defer(() -> post("/v1/payments/" + reference + "/confirm", "{}"))
        .retryWhen(
            Retry.max(configuration.retryBudget())
                .filter(GatewayClient::isTransportFailure)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
  }

  private Mono<GatewayResponse> post(String path, String json) {
    final HttpRequest request =
        HttpRequest.newBuilder(configuration.resolve(path))
            .timeout(configuration.timeout())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(BodyPublishers.ofString(json))
            .build();
    return Mono.fromFuture(() -> http.sendAsync(request, BodyHandlers.ofString()))
        .timeout(configuration.timeout())
        .onErrorMap(GatewayClient::isNotAlreadyTranslated, GatewayClient::translate)
        .map(this::parse);
  }

  private GatewayResponse parse(HttpResponse<String> response) {
    if (response.statusCode() < HTTP_OK_FIRST || response.statusCode() > HTTP_OK_LAST) {
      throw new PaymentProviderException(
          PaymentProviderException.Kind.UNREACHABLE,
          "Gateway answered with HTTP status " + response.statusCode(),
          null);
    }
    try {
      final GatewayResponse parsed = mapper.readValue(response.body(), GatewayResponse.class);
      if (parsed == null) {
        throw malformed(null);
      }
      return parsed;
    } catch (JacksonException e) {
      throw malformed(e);
    }
  }

  private String writeJson(GatewayRequest request) {
    try {
      return mapper.writeValueAsString(request);
    } catch (JacksonException e) {
      throw new IllegalStateException("Could not serialise a gateway request", e);
    }
  }

  private static PaymentProviderException malformed(Throwable cause) {
    return new PaymentProviderException(
        PaymentProviderException.Kind.MALFORMED_RESPONSE,
        "Gateway answered with something that could not be understood",
        cause);
  }

  private static boolean isNotAlreadyTranslated(Throwable error) {
    return !(error instanceof PaymentProviderException);
  }

  private static boolean isTransportFailure(Throwable error) {
    return error instanceof PaymentProviderException failure
        && failure.kind() != PaymentProviderException.Kind.MALFORMED_RESPONSE;
  }

  /**
   * Turns a transport error into the exception checkout expects. Anything that is not a recognised
   * transport error is a programming error and is left as it is.
   */
  private static Throwable translate(Throwable error) {
    return kindOf(error)
        .<Throwable>map(
            kind ->
                new PaymentProviderException(kind, "Gateway could not be reached: " + kind, error))
        .orElse(error);
  }

  private static Optional<PaymentProviderException.Kind> kindOf(Throwable error) {
    return switch (error) {
      case TimeoutException _, HttpTimeoutException _ ->
          Optional.of(PaymentProviderException.Kind.TIMEOUT);
      case IOException _ -> Optional.of(PaymentProviderException.Kind.UNREACHABLE);
      default -> Optional.empty();
    };
  }
}
