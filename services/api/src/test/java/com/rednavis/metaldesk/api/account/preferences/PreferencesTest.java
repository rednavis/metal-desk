package com.rednavis.metaldesk.api.account.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.rednavis.metaldesk.api.cart.CartTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** A signed-in customer's preferences follow the account (BRD FR-1.6 to FR-1.8). */
class PreferencesTest extends CartTestSupport {

  private static final String URI = "/api/account/preferences";
  private static final String THEME = "theme";
  private static final String LOCALE = "locale";
  private static final String CURRENCY = "currency";
  private static final String CODE = "code";
  private static final String DARK = "DARK";
  private static final String DOLLARS = "USD";

  private Called save(String token, Map<String, Object> body) {
    return call(HttpMethod.PUT, URI, null, token, body);
  }

  private Called read(String token) {
    return call(HttpMethod.GET, URI, null, token, null);
  }

  @Test
  void customerWhoChoseNothingHasNoPreferences() {
    final Called called = read(signedInToken());

    assertEquals(200, called.status());
    assertEquals(Map.of(), called.body());
  }

  @Test
  void savedPreferencesAreReadBackBySameCustomerInNewSession() {
    final String email = freshEmail();
    registerAndVerify(email);
    final String firstSession = signIn(email, PASSWORD);
    save(firstSession, Map.of(THEME, DARK, LOCALE, "de", CURRENCY, DOLLARS));

    final String secondSession = signIn(email, PASSWORD);
    final Called read = read(secondSession);

    assertEquals(DARK, read.body().get(THEME));
    assertEquals("de", read.body().get(LOCALE));
    assertEquals(DOLLARS, read.body().get(CURRENCY));
  }

  @Test
  void savingReplacesEverythingSoFieldLeftOutIsCleared() {
    final String token = signedInToken();
    save(token, Map.of(THEME, DARK, CURRENCY, DOLLARS));

    save(token, Map.of(LOCALE, "en"));
    final Called read = read(token);

    assertNull(read.body().get(THEME));
    assertNull(read.body().get(CURRENCY));
    assertEquals("en", read.body().get(LOCALE));
  }

  @Test
  void valuesAreNormalisedAndCustomersAreIndependent() {
    final String mine = signedInToken();
    final String theirs = signedInToken();

    save(mine, Map.of(THEME, "dark", LOCALE, "DE", CURRENCY, "usd"));

    final Called read = read(mine);
    assertEquals(DARK, read.body().get(THEME));
    assertEquals("de", read.body().get(LOCALE));
    assertEquals(DOLLARS, read.body().get(CURRENCY));
    assertEquals(Map.of(), read(theirs).body());
  }

  @Test
  void valuesThePlatformCannotHonourAreRefused() {
    final String token = signedInToken();

    for (final Map<String, Object> bad :
        java.util.List.<Map<String, Object>>of(
            Map.of(THEME, "neon"), Map.of(LOCALE, "xx"), Map.of(CURRENCY, "XXX"))) {
      final Called called = save(token, bad);
      assertEquals(400, called.status(), bad.toString());
      assertEquals(
          bad.containsKey(CURRENCY) ? "currency.unsupported" : "preferences.invalid",
          called.body().get(CODE));
    }
    assertEquals(Map.of(), read(token).body());
  }

  @Test
  void anonymousRequestsAreRefused() {
    assertEquals(401, read(null).status());
    assertEquals(401, save(null, Map.of(THEME, DARK)).status());
  }
}
