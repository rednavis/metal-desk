package com.rednavis.metaldesk.share.domain.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthCredentialTest {

  private static final AuthIdentifier IDENTIFIER = AuthIdentifier.parse("ann@example.com");
  private static final String HASH = "{bcrypt}$2a$10$abcdefghijklmnopqrstuv";

  @Test
  void keepsItsFields() {
    final AuthCredential credential =
        new AuthCredential(IDENTIFIER, HASH, AuthCredential.State.ACTIVE);
    assertEquals(IDENTIFIER, credential.identifier());
    assertEquals(HASH, credential.passwordHash());
    assertEquals(AuthCredential.State.ACTIVE, credential.state());
  }

  @Test
  void nullIdentifierIsRefused() {
    assertEquals(
        "auth-credential.identifier-missing",
        assertThrows(
                ValidationException.class,
                () -> new AuthCredential(null, HASH, AuthCredential.State.ACTIVE))
            .code());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void blankHashIsRefused(String hash) {
    assertEquals(
        "auth-credential.hash-blank",
        assertThrows(
                ValidationException.class,
                () -> new AuthCredential(IDENTIFIER, hash, AuthCredential.State.ACTIVE))
            .code());
  }

  @Test
  void nullStateIsRefused() {
    assertEquals(
        "auth-credential.state-missing",
        assertThrows(ValidationException.class, () -> new AuthCredential(IDENTIFIER, HASH, null))
            .code());
  }

  @Test
  void describingCredentialDoesNotLeakHash() {
    final String text =
        new AuthCredential(IDENTIFIER, HASH, AuthCredential.State.ACTIVE).toString();
    assertFalse(text.contains(HASH));
    assertTrue(text.contains("***"));
  }

  @Test
  void exposesNoMethodTakingPlaintextPassword() {
    // Synthetic members (e.g. Jacoco's $jacocoInit) are instrumentation, not API.
    for (final Method method : AuthCredential.class.getDeclaredMethods()) {
      if (method.isSynthetic()) {
        continue;
      }
      assertFalse(
          method.getName().toLowerCase(Locale.ROOT).contains("verify"),
          () -> "verification method found: " + method);
      assertFalse(
          Arrays.stream(method.getParameterTypes())
              .anyMatch(type -> type == String.class || type == char[].class),
          () -> "method takes a string or char[] and could accept a plaintext password: " + method);
    }
  }

  @Test
  void onlyAnActiveCredentialCanSignIn() {
    assertTrue(AuthCredential.State.ACTIVE.canSignIn());
    assertFalse(AuthCredential.State.DISABLED.canSignIn());
  }
}
