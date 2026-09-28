package com.rednavis.metaldesk.share.domain.customer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VerificationStateTest {

  @Test
  void unverifiedAccountCannotCheckOut() {
    assertFalse(VerificationState.UNVERIFIED.canCheckout());
  }

  @Test
  void verifiedAccountCanCheckOut() {
    assertTrue(VerificationState.VERIFIED.canCheckout());
  }
}
