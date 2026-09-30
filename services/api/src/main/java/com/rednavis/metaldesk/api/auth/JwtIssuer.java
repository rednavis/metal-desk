package com.rednavis.metaldesk.api.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Mints the access token.
 *
 * <p>The claim set is exactly: issuer, audience, subject (the customer id), issued-at, expiry, and
 * {@value #STATE_CLAIM} (the customer's verification state, so checkout can read it without a
 * lookup). It carries <strong>no email, no phone and nothing derived from the password</strong>: a
 * token is readable by anyone who holds it. The lifetime is short because there is no revocation.
 */
@Component
public class JwtIssuer {

  /** The claim holding the customer's {@code VerificationState} name. */
  public static final String STATE_CLAIM = "verification";

  private final JwtProperties properties;
  private final Clock clock;
  private final JwtEncoder encoder;

  /**
   * Creates the issuer.
   *
   * @param properties the issuer, audience and lifetime
   * @param key the signing key
   * @param clock the source of time
   */
  public JwtIssuer(JwtProperties properties, SecretKey key, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  /**
   * Issues a token for a customer.
   *
   * @param customer the authenticated customer
   * @return the token and when it expires
   */
  public IssuedToken issue(AuthenticatedCustomer customer) {
    final Instant issuedAt = clock.instant();
    final Instant expiresAt = issuedAt.plus(properties.ttl());
    final JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            .subject(customer.id().value())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim(STATE_CLAIM, customer.verification().name())
            .build();
    final String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new IssuedToken(token, expiresAt, properties.ttl());
  }
}
