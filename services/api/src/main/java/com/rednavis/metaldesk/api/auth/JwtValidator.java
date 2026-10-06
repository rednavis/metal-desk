package com.rednavis.metaldesk.api.auth;

import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Validates the access token of every request and reads who it is for.
 *
 * <p>The decoder ({@link JwtConfiguration#jwtDecoder}) makes all four checks: the
 * <strong>signature</strong>, the <strong>expiry</strong>, the <strong>issuer</strong> and the
 * <strong>audience</strong>. Checking only the signature would accept a token minted for another
 * environment that happens to share a key. On top of that, a token without a subject or with an
 * unreadable verification state is invalid too, and the caller learns no more than "invalid".
 */
@Component
@RequiredArgsConstructor
public class JwtValidator {

  private final ReactiveJwtDecoder decoder;

  /**
   * Validates a token and reads who it is for.
   *
   * @param token the compact token; null or blank is invalid
   * @return the customer, or an error signal if the token is invalid in any way
   */
  public Mono<AuthenticatedCustomer> validate(String token) {
    return token == null || token.isBlank()
        ? Mono.error(new BadJwtException("Missing token"))
        : decoder.decode(token).map(JwtValidator::customerOf);
  }

  /**
   * Turns a validated token into the request's authentication.
   *
   * @param jwt the validated token
   * @return the authentication, whose principal is the customer
   */
  public Mono<AbstractAuthenticationToken> authentication(Jwt jwt) {
    return Mono.fromSupplier(() -> new CustomerAuthentication(customerOf(jwt)));
  }

  private static AuthenticatedCustomer customerOf(Jwt jwt) {
    final String state = jwt.getClaimAsString(JwtIssuer.STATE_CLAIM);
    final boolean known =
        state != null
            && Arrays.stream(VerificationState.values()).anyMatch(v -> v.name().equals(state));
    if (jwt.getSubject() == null || !known) {
      throw new BadJwtException("Token claims are not readable");
    }
    try {
      return new AuthenticatedCustomer(
          new CustomerId(jwt.getSubject()), VerificationState.valueOf(state));
    } catch (ValidationException blankSubject) {
      throw new BadJwtException("Token claims are not readable", blankSubject);
    }
  }
}
