package com.rednavis.metaldesk.api.auth;

import com.rednavis.metaldesk.api.web.ApiErrorEnvelope;
import com.rednavis.metaldesk.api.web.CorrelationId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Sign-in, sign-out and "who am I" (BRD FR-2.1, FR-2.2).
 *
 * <p>The failure response is built from one {@link SignInOutcome.Rejected}, with one code, one
 * message and one status, whatever went wrong.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  /** The single error code of every failed sign-in. */
  public static final String BAD_CREDENTIALS = "auth.invalid-credentials";

  /** The error code of a locked-out source. */
  public static final String THROTTLED = "auth.throttled";

  private static final String BEARER = "Bearer";

  private final AuthenticationService service;
  private final ClientAddressResolver addresses;

  /**
   * Signs a customer in. Public.
   *
   * @param request the identifier and password; the body may even be missing
   * @param http the request, whose source address keys the throttle
   * @param correlation the caller's correlation id, if any
   * @return 200 with a token; 401 with the generic envelope for any failure; 429 when the source is
   *     locked out
   */
  @PostMapping("/sign-in")
  public Mono<ResponseEntity<Object>> signIn(
      @RequestBody(required = false) SignInRequest request,
      ServerHttpRequest http,
      @RequestHeader(name = CorrelationId.HEADER, required = false) String correlation) {
    return service
        .signIn(request, addresses.resolve(http))
        .map(outcome -> respond(outcome, CorrelationId.choose(correlation)));
  }

  /**
   * Signs out. Tokens are stateless and cannot be revoked in this phase, so this only confirms; the
   * client discards its token.
   *
   * @return 204
   */
  @PostMapping("/sign-out")
  public Mono<ResponseEntity<Void>> signOut() {
    return Mono.just(ResponseEntity.noContent().build());
  }

  /**
   * Swaps the bearer token for a new one with a full lifetime, so a customer who keeps using the
   * site stays signed in and one who stops is signed out when the last token expires.
   *
   * @param customer the authenticated customer
   * @param correlation the caller's correlation id, if any
   * @return 200 with a new token; 401 if the customer may no longer sign in
   */
  @PostMapping("/refresh")
  public Mono<ResponseEntity<Object>> refresh(
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @RequestHeader(name = CorrelationId.HEADER, required = false) String correlation) {
    return service
        .refresh(customer)
        .<ResponseEntity<Object>>map(
            token ->
                ResponseEntity.ok(
                    new SignInResponse(token.value(), BEARER, token.lifetime().toSeconds())))
        .defaultIfEmpty(
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                    new ApiErrorEnvelope(
                        "auth.unauthorized",
                        "Authentication is required",
                        CorrelationId.choose(correlation))));
  }

  /**
   * Says who the bearer token is for.
   *
   * @param customer the authenticated customer
   * @return the customer id and verification state
   */
  @GetMapping("/me")
  public Mono<CustomerView> me(@AuthenticationPrincipal AuthenticatedCustomer customer) {
    return Mono.just(new CustomerView(customer.id().value(), customer.verification()));
  }

  private static ResponseEntity<Object> respond(SignInOutcome outcome, String correlationId) {
    return switch (outcome) {
      case SignInOutcome.Success success ->
          ResponseEntity.ok(
              new SignInResponse(
                  success.token().value(), BEARER, success.token().lifetime().toSeconds()));
      case SignInOutcome.Rejected() ->
          ResponseEntity.status(HttpStatus.UNAUTHORIZED)
              .body(new ApiErrorEnvelope(BAD_CREDENTIALS, "Invalid credentials", correlationId));
      case SignInOutcome.Throttled throttled ->
          ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
              .header(
                  HttpHeaders.RETRY_AFTER, Long.toString(throttled.retryAfter().toSeconds() + 1))
              .body(
                  new ApiErrorEnvelope(
                      THROTTLED, "Too many attempts, try again later", correlationId));
    };
  }
}
