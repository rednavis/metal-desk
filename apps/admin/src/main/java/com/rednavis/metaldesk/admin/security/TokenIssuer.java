package com.rednavis.metaldesk.admin.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
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
 * Mints the staff access token.
 *
 * <p>The claim set is exactly: issuer, audience, subject (the user id), issued-at, expiry, {@value
 * #LOGIN_CLAIM} and {@value #ROLE_CLAIM}. It carries <strong>no email and nothing derived from the
 * password</strong>: a token is readable by anyone who holds it. The lifetime is short because
 * there is no revocation.
 */
@Component
public class TokenIssuer {

  /** The claim holding the login name. */
  public static final String LOGIN_CLAIM = "login";

  /** The claim holding the {@code UserRole} name. */
  public static final String ROLE_CLAIM = "role";

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
  public TokenIssuer(JwtProperties properties, SecretKey key, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  /**
   * Issues a token for a user.
   *
   * @param staff the authenticated user
   * @return the token and when it expires
   */
  public IssuedToken issue(StaffPrincipal staff) {
    final Instant issuedAt = clock.instant();
    final Instant expiresAt = issuedAt.plus(properties.ttl());
    final JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            .subject(staff.id())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim(LOGIN_CLAIM, staff.login())
            .claim(ROLE_CLAIM, staff.role().name())
            .build();
    final String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new IssuedToken(token, expiresAt, properties.ttl());
  }

  /**
   * A freshly minted access token.
   *
   * @param value the compact signed token
   * @param expiresAt when it stops being valid
   * @param lifetime how long it was issued for
   */
  public record IssuedToken(String value, Instant expiresAt, Duration lifetime) {

    /** Redacts the token, so a logged value does not leak it. */
    @Override
    public String toString() {
      return "IssuedToken[expiresAt=" + expiresAt + ']';
    }
  }
}
