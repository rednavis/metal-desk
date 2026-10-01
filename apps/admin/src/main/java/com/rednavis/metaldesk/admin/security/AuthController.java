package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.admin.web.CorrelationId;
import com.rednavis.metaldesk.admin.web.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in and sign-out for back-office users.
 *
 * <p>The failure response is built from one {@link SignInOutcome.Rejected}, with one code, one
 * message and one status, whatever went wrong.
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

  /** The single error code of every failed sign-in. */
  public static final String BAD_CREDENTIALS = "auth.invalid-credentials";

  /** The error code of a locked-out attempt. */
  public static final String THROTTLED = "auth.throttled";

  private final AuthenticationService service;

  /**
   * Signs a back-office user in. Public.
   *
   * @param request the login and password; the body may even be missing
   * @param http the request, whose source address keys the throttle
   * @return 200 with a token; 401 with the generic envelope for any failure; 429 when locked out
   */
  @PostMapping("/sign-in")
  public ResponseEntity<Object> signIn(
      @RequestBody(required = false) SignInRequest request, HttpServletRequest http) {
    final String correlationId = CorrelationId.choose(http.getHeader(CorrelationId.HEADER));
    return switch (service.signIn(request, http.getRemoteAddr())) {
      case SignInOutcome.Success success -> ResponseEntity.ok(success.response());
      case SignInOutcome.Rejected() ->
          ResponseEntity.status(HttpStatus.UNAUTHORIZED)
              .body(new ErrorEnvelope(BAD_CREDENTIALS, "Invalid credentials", correlationId));
      case SignInOutcome.Throttled throttled ->
          ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
              .header(
                  HttpHeaders.RETRY_AFTER, Long.toString(throttled.retryAfter().toSeconds() + 1))
              .body(
                  new ErrorEnvelope(
                      THROTTLED, "Too many attempts, try again later", correlationId));
    };
  }

  /**
   * Signs out. Tokens are stateless and cannot be revoked, so this only confirms; the client
   * discards its token.
   *
   * @return 204
   */
  @PostMapping("/sign-out")
  public ResponseEntity<Void> signOut() {
    return ResponseEntity.noContent().build();
  }
}
