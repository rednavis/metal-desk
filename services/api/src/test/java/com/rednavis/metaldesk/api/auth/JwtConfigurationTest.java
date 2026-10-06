package com.rednavis.metaldesk.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/** The signing key is taken from configuration, or generated; a bad one stops the start. */
class JwtConfigurationTest {

  private final JwtConfiguration configuration = new JwtConfiguration();

  private static JwtProperties withKey(String key) {
    return new JwtProperties("iss", "aud", Duration.ofMinutes(30), key);
  }

  @Test
  void blankKeyGivesRandomKeyOf32Bytes() {
    final SecretKey first = configuration.jwtSigningKey(withKey(""));
    final SecretKey second = configuration.jwtSigningKey(withKey(" "));

    assertEquals(32, first.getEncoded().length);
    assertFalse(Arrays.equals(first.getEncoded(), second.getEncoded()));
  }

  @Test
  void configuredKeyIsUsedAsGiven() {
    final byte[] bytes = new byte[40];
    Arrays.fill(bytes, (byte) 7);

    final SecretKey key =
        configuration.jwtSigningKey(withKey(Base64.getEncoder().encodeToString(bytes)));

    assertEquals(40, key.getEncoded().length);
  }

  @Test
  void keyThatIsNotBase64IsRefused() {
    assertThrows(
        IllegalStateException.class, () -> configuration.jwtSigningKey(withKey("!!notb64")));
  }

  @Test
  void keyThatIsTooShortIsRefused() {
    final String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThrows(IllegalStateException.class, () -> configuration.jwtSigningKey(withKey(shortKey)));
  }

  @Test
  void stringFormNeverShowsTheKey() {
    assertFalse(withKey("topsecret").toString().contains("topsecret"));
  }
}
