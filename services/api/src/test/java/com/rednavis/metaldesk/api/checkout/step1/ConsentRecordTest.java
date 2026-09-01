package com.rednavis.metaldesk.api.checkout.step1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** A consent needs the policy accepted, and says which version and when. */
class ConsentRecordTest {

  private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

  private static String codeOf(boolean accepted, String version, Instant at) {
    return assertThrows(ValidationException.class, () -> new ConsentRecord(accepted, version, at))
        .code();
  }

  @Test
  void unacceptedPolicyIsRefused() {
    assertEquals("consent.privacy-required", codeOf(false, "v1", NOW));
  }

  @Test
  void missingVersionOrTimeIsRefused() {
    assertEquals("consent.incomplete", codeOf(true, null, NOW));
    assertEquals("consent.incomplete", codeOf(true, " ", NOW));
    assertEquals("consent.incomplete", codeOf(true, "v1", null));
  }

  @Test
  void completeConsentIsKept() {
    assertEquals("v1", new ConsentRecord(true, "v1", NOW).policyVersion());
  }
}
