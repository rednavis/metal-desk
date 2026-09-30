package com.rednavis.metaldesk.admin.security;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rednavis.metaldesk.admin.AdminTestSupport;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Authentication is the proxy's identity and nothing else: a customer's token is worth nothing
 * here, and the development override is off unless it is configured on.
 */
class StaffAuthenticationTest extends AdminTestSupport {

  private static final String SEGMENT = "x";
  private static final SecureRandom RANDOM = new SecureRandom();

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  /** A token shaped exactly as services/api mints them (T-032): HS256, the same claim set. */
  private static String customerJwt() throws GeneralSecurityException {
    final Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
    final long issued = Instant.now().getEpochSecond();
    final String header =
        base64.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    final String claims =
        base64.encodeToString(
            ("{\"iss\":\"metal-desk-api\",\"aud\":\"metal-desk-storefront\",\"sub\":\"cust-1\","
                    + "\"iat\":"
                    + issued
                    + ",\"exp\":"
                    + (issued + 900)
                    + ",\"verification\":\"VERIFIED\"}")
                .getBytes(StandardCharsets.UTF_8));
    final Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(randomKey(), "HmacSHA256"));
    final String signature =
        base64.encodeToString(
            mac.doFinal((header + "." + claims).getBytes(StandardCharsets.UTF_8)));
    return header + "." + claims + "." + signature;
  }

  private static byte[] randomKey() {
    final byte[] key = new byte[32];
    RANDOM.nextBytes(key);
    return key;
  }

  private List<String[]> adminEndpoints() {
    return mappings.getHandlerMethods().keySet().stream()
        .filter(info -> info.getPathPatternsCondition() != null)
        .flatMap(
            info ->
                Objects.requireNonNull(info.getPathPatternsCondition()).getPatternValues().stream()
                    .filter(path -> path.startsWith("/api/admin"))
                    .flatMap(
                        path -> methods(info).stream().map(method -> new String[] {method, path})))
        .toList();
  }

  private static List<String> methods(RequestMappingInfo info) {
    return info.getMethodsCondition().getMethods().stream().map(Enum::name).toList();
  }

  private static String concrete(String path) {
    return path.replaceAll("\\{[^}]+}", SEGMENT);
  }

  @Test
  void endpointListIsNotEmpty() {
    assertFalse(adminEndpoints().isEmpty());
  }

  @Test
  void everyAdminEndpointRefusesRequestWithNoIdentity() {
    for (final String[] endpoint : adminEndpoints()) {
      client
          .method(HttpMethod.valueOf(endpoint[0]))
          .uri(concrete(endpoint[1]))
          .exchange()
          .expectStatus()
          .isUnauthorized()
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("auth.unauthorized");
    }
  }

  @Test
  void everyAdminEndpointRefusesValidCustomerJwt() throws GeneralSecurityException {
    final String bearer = "Bearer " + customerJwt();
    for (final String[] endpoint : adminEndpoints()) {
      client
          .method(HttpMethod.valueOf(endpoint[0]))
          .uri(concrete(endpoint[1]))
          .header("Authorization", bearer)
          .exchange()
          .expectStatus()
          .isUnauthorized();
    }
  }

  @Test
  void developmentOverrideIsOffWhenNothingConfiguresIt() {
    client.get().uri("/api/admin/tiers").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void malformedProxyIdentityIsRefused() {
    client
        .get()
        .uri("/api/admin/tiers")
        .header(IAP_HEADER, "accounts.google.com:not-an-email")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void theIdentityIsShownBackToTheStaffMember() {
    get("/api/admin/me")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.email")
        .isEqualTo("staff@example.com");
  }

  @Test
  void proxyIdentityIsAccepted() {
    get("/api/admin/tiers").expectStatus().isOk();
  }

  @Test
  void healthStaysOpen() {
    client.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }
}
