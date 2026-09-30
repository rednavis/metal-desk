package com.rednavis.metaldesk.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the staff identity, under {@code metaldesk.admin.staff}.
 *
 * @param identityHeader the request header the Identity-Aware Proxy puts the staff member in
 * @param devIdentity the local-development override
 */
@ConfigurationProperties("metaldesk.admin.staff")
public record StaffProperties(
    @DefaultValue("X-Goog-Authenticated-User-Email") String identityHeader,
    @DefaultValue DevIdentity devIdentity) {

  /**
   * The development override: when enabled, a request with no proxy header is treated as coming
   * from {@code email}. It is a complete bypass of authentication and is off unless switched on.
   *
   * @param enabled whether the override applies; false by default
   * @param email the identity to assume
   */
  public record DevIdentity(
      @DefaultValue("false") boolean enabled, @DefaultValue("") String email) {}
}
