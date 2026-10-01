package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.share.domain.user.UserRole;
import java.util.Arrays;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Reads who a validated token is for. The decoder has already checked signature, expiry, issuer and
 * audience; on top of that a token without a subject, a login or a known role is invalid, and the
 * caller learns no more than "invalid".
 */
@Component
public class StaffTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    final String login = jwt.getClaimAsString(TokenIssuer.LOGIN_CLAIM);
    final String role = jwt.getClaimAsString(TokenIssuer.ROLE_CLAIM);
    final boolean known =
        role != null && Arrays.stream(UserRole.values()).anyMatch(r -> r.name().equals(role));
    if (jwt.getSubject() == null || login == null || login.isBlank() || !known) {
      throw new InvalidBearerTokenException("Token claims are not readable");
    }
    return new StaffAuthentication(
        new StaffPrincipal(jwt.getSubject(), login, UserRole.valueOf(role)));
  }
}
