package com.rednavis.metaldesk.api.auth;

import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated state of a request whose token validated: the principal is the customer.
 *
 * <p>It keeps the customer as plain serializable values and rebuilds the {@link
 * AuthenticatedCustomer} on demand.
 */
public final class CustomerAuthentication extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;

  private final String customerId;
  private final VerificationState verification;

  /**
   * Creates an authenticated token.
   *
   * @param customer who the request is from
   */
  public CustomerAuthentication(AuthenticatedCustomer customer) {
    super(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    this.customerId = customer.id().value();
    this.verification = customer.verification();
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return "";
  }

  @Override
  public AuthenticatedCustomer getPrincipal() {
    return new AuthenticatedCustomer(new CustomerId(customerId), verification);
  }
}
