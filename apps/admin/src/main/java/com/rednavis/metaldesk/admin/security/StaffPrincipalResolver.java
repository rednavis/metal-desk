package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reads the staff identity from the header the Identity-Aware Proxy sets.
 *
 * <p>The proxy sends {@code accounts.google.com:<email>}; the prefix is dropped. The {@code
 * Authorization} header is never read, so a customer's JWT from {@code services/api} gives no
 * access here whatever it contains.
 *
 * <p><strong>Trust.</strong> The header is believed as it is, which is sound only because a
 * deployment makes the service reachable through the proxy alone (T-076). Verifying the proxy's
 * signed assertion as well is the hardening that task owns.
 *
 * <p>With the development override switched on, a request without the header is treated as coming
 * from the configured identity. The override is off by default, and a warning is logged at startup
 * if it is on.
 */
@Slf4j
@Component
public class StaffPrincipalResolver {

  private final StaffProperties properties;

  /**
   * Creates the resolver.
   *
   * @param properties the header name and the development override
   */
  public StaffPrincipalResolver(StaffProperties properties) {
    this.properties = properties;
    if (properties.devIdentity().enabled()) {
      log.warn(
          "DEVELOPMENT STAFF IDENTITY IS ENABLED: requests without a proxy header are treated as"
              + " staff. This must never be on in a deployment.");
    }
  }

  /**
   * Finds who is calling.
   *
   * @param request the request
   * @return the staff member, or empty if the request carries no valid staff identity
   */
  public Optional<StaffPrincipal> resolve(HttpServletRequest request) {
    final String header = request.getHeader(properties.identityHeader());
    final StaffProperties.DevIdentity dev = properties.devIdentity();
    Optional<StaffPrincipal> resolved = Optional.empty();
    if (header != null && !header.isBlank()) {
      resolved = parse(header.substring(header.indexOf(':') + 1));
    } else if (dev.enabled()) {
      resolved = parse(dev.email());
    }
    return resolved;
  }

  private static Optional<StaffPrincipal> parse(String email) {
    Optional<StaffPrincipal> parsed;
    try {
      parsed = Optional.of(new StaffPrincipal(new EmailAddress(email.strip()).value()));
    } catch (ValidationException e) {
      parsed = Optional.empty();
    }
    return parsed;
  }
}
