package com.rednavis.metaldesk.payments.http;

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
 * The thin, non-blocking HTTP client every provider adapter shares: it posts JSON and returns the
 * parsed answer, and turns every way the transport can fail into a {@link PaymentProviderException}
 * of the right {@link PaymentProviderException.Kind}. It knows nothing about what an answer means;
 * the adapter that owns the wire types decides that.
 *
 * <p>Nothing here blocks: each call is the JDK client's asynchronous send, bridged to a {@link
 * Mono}. The timeout is enforced on that {@code Mono}, so it fires whatever the HTTP client does,
 * and cancelling the {@code Mono} cancels the request.
 *
 * <p><strong>Retries are the caller's explicit choice.</strong> {@link #post} makes one attempt and
 * is what taking a payment must use: once the request may have reached the provider, a timeout does
 * not mean the payment failed, and repeating it could take the money twice. {@link #postIdempotent}
 * is for calls that only read state, and retries a transport failure up to the endpoint's budget,
 * never a malformed answer.
 *
 * <p>Unknown fields in an answer are ignored and never kept, so nothing a provider adds can end up
 * stored.
 */
public final class JsonHttpClient {

  private static final int HTTP_OK_FIRST = 200;
  private static final int HTTP_OK_LAST = 299;

  private final HttpEndpoint endpoint;
  private final HttpClient http;
  private final JsonMapper mapper;

  /**
   * Creates a client for an endpoint.
   *
   * @param endpoint where the provider is and how patient to be
   */
  public JsonHttpClient(HttpEndpoint endpoint) {
    this.endpoint = endpoint;
    this.http = HttpClient.newBuilder().connectTimeout(endpoint.timeout()).build();
    this.mapper =
        JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  /**
   * Posts a JSON body once. Never retried; use this for anything that changes state.
   *
   * @param <T> the type of the parsed answer
   * @param path the path, starting with a slash
   * @param body the request, serialised as JSON
   * @param responseType the type the answer is parsed into
   * @return the parsed answer, or an error of type {@link PaymentProviderException}
   */
  public <T> Mono<T> post(String path, Object body, Class<T> responseType) {
    return Mono.defer(() -> send(path, writeJson(body), responseType));
  }

  /**
   * Posts a JSON body that is safe to repeat, retrying a transport failure up to the endpoint's
   * retry budget. A malformed answer is not retried.
   *
   * @param <T> the type of the parsed answer
   * @param path the path, starting with a slash
   * @param body the request, serialised as JSON
   * @param responseType the type the answer is parsed into
   * @return the parsed answer, or an error of type {@link PaymentProviderException}
   */
  public <T> Mono<T> postIdempotent(String path, Object body, Class<T> responseType) {
    return post(path, body, responseType)
        .retryWhen(
            Retry.max(endpoint.retryBudget())
                .filter(JsonHttpClient::isTransportFailure)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
  }

  private <T> Mono<T> send(String path, String json, Class<T> responseType) {
    final HttpRequest request =
        HttpRequest.newBuilder(endpoint.resolve(path))
            .timeout(endpoint.timeout())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(BodyPublishers.ofString(json))
            .build();
    return Mono.fromFuture(() -> http.sendAsync(request, BodyHandlers.ofString()))
        .timeout(endpoint.timeout())
        .onErrorMap(JsonHttpClient::isNotAlreadyTranslated, JsonHttpClient::translate)
        .map(response -> parse(response, responseType));
  }

  private <T> T parse(HttpResponse<String> response, Class<T> responseType) {
    if (response.statusCode() < HTTP_OK_FIRST || response.statusCode() > HTTP_OK_LAST) {
      throw new PaymentProviderException(
          PaymentProviderException.Kind.UNREACHABLE,
          "Provider answered with HTTP status " + response.statusCode(),
          null);
    }
    try {
      final T parsed = mapper.readValue(response.body(), responseType);
      if (parsed == null) {
        throw malformed(null);
      }
      return parsed;
    } catch (JacksonException e) {
      throw malformed(e);
    }
  }

  private String writeJson(Object body) {
    try {
      return mapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalStateException("Could not serialise a provider request", e);
    }
  }

  private static PaymentProviderException malformed(Throwable cause) {
    return new PaymentProviderException(
        PaymentProviderException.Kind.MALFORMED_RESPONSE,
        "Provider answered with something that could not be understood",
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
                new PaymentProviderException(kind, "Provider could not be reached: " + kind, error))
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
