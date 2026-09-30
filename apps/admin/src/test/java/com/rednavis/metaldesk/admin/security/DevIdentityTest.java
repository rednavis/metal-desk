package com.rednavis.metaldesk.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** The resolver reads the proxy header and honours the development override only when asked. */
class DevIdentityTest {

  private static final String HEADER = "X-Goog-Authenticated-User-Email";

  private static StaffPrincipalResolver resolver(boolean enabled) {
    return new StaffPrincipalResolver(
        new StaffProperties(HEADER, new StaffProperties.DevIdentity(enabled, "dev@example.com")));
  }

  @Test
  void stripsTheProxyPrefix() {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER, "accounts.google.com:staff@example.com");

    assertEquals(
        Optional.of(new StaffPrincipal("staff@example.com")), resolver(false).resolve(request));
  }

  @Test
  void noHeaderMeansNobodyUnlessTheOverrideIsOn() {
    final MockHttpServletRequest request = new MockHttpServletRequest();

    assertTrue(resolver(false).resolve(request).isEmpty());
    assertEquals(
        Optional.of(new StaffPrincipal("dev@example.com")), resolver(true).resolve(request));
  }

  @Test
  void overrideNeverBeatsRealProxyIdentity() {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER, "accounts.google.com:staff@example.com");

    assertEquals("staff@example.com", resolver(true).resolve(request).orElseThrow().email());
  }

  @Test
  void blankOverrideIdentityIsNobody() {
    final StaffPrincipalResolver blank =
        new StaffPrincipalResolver(
            new StaffProperties(HEADER, new StaffProperties.DevIdentity(true, "")));

    assertTrue(blank.resolve(new MockHttpServletRequest()).isEmpty());
  }
}
