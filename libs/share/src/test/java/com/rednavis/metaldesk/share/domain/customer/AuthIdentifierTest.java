package com.rednavis.metaldesk.share.domain.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthIdentifierTest {

  private static final String ANN = "ann@example.com";
  private static final String PHONE = "+491701234567";

  @Test
  void emailNormalisationIsCaseInsensitive() {
    final AuthIdentifier upper = AuthIdentifier.parse("Ann@Example.COM");
    final AuthIdentifier lower = AuthIdentifier.parse(" ann@example.com ");

    assertEquals(ANN, upper.normalised());
    assertEquals(upper.normalised(), lower.normalised());
    assertEquals(upper, lower);
  }

  @Test
  void phoneNormalisationStripsFormatting() {
    final AuthIdentifier formatted = AuthIdentifier.parse("+49 (170) 123-4567");
    final AuthIdentifier plain = AuthIdentifier.parse(PHONE);

    assertEquals(PHONE, formatted.normalised());
    assertEquals(formatted, plain);
  }

  @Test
  void atSignMakesEmailAndAnythingElsePhone() {
    assertInstanceOf(AuthIdentifier.Email.class, AuthIdentifier.parse(ANN));
    assertInstanceOf(AuthIdentifier.Phone.class, AuthIdentifier.parse(PHONE));
  }

  @Test
  void emailAndPhoneWithDifferentKeysAreNotEqual() {
    final AuthIdentifier email = new AuthIdentifier.Email(new EmailAddress(ANN));
    final AuthIdentifier phone = new AuthIdentifier.Phone(new PhoneNumber(PHONE));

    assertNotEquals(email.normalised(), phone.normalised());
    assertNotEquals(email, phone);
  }

  @Test
  void switchOverBothCasesIsExhaustive() {
    final AuthIdentifier identifier = AuthIdentifier.parse(ANN);
    final String kind =
        switch (identifier) {
          case AuthIdentifier.Email _ -> "email";
          case AuthIdentifier.Phone _ -> "phone";
        };
    assertEquals("email", kind);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "not-a-phone", "ann@", "@example.com", "12345"})
  void everyBadInputFailsWithTheSameCode(String raw) {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> AuthIdentifier.parse(raw));
    assertEquals("auth-identifier.malformed", failure.code());
  }

  @Test
  void nullCaseValueIsRefused() {
    assertThrows(ValidationException.class, () -> new AuthIdentifier.Email(null));
    assertThrows(ValidationException.class, () -> new AuthIdentifier.Phone(null));
  }
}
