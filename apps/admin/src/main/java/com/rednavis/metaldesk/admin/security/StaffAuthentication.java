package com.rednavis.metaldesk.admin.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** The request's authentication: an already validated staff token, whose principal is the user. */
public final class StaffAuthentication extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;

  private final StaffPrincipal principal;

  /**
   * Creates the authentication, already authenticated.
   *
   * @param principal the user the token is for
   */
  public StaffAuthentication(StaffPrincipal principal) {
    super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
    this.principal = principal;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return "";
  }

  @Override
  public StaffPrincipal getPrincipal() {
    return principal;
  }
}
