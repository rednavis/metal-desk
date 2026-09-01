package com.rednavis.metaldesk.api.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.checkout.dto.Step1Request;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The validation matrix of step 1 (BRD FR-4.1, FR-4.3), over HTTP against real MongoDB. */
class CheckoutStep1Test extends CheckoutTestSupport {

  private static final String PHONE = "contact.phone";
  private static final String CITY = "city";
  private static final String COUNTRY = "country";
  private static final String POSTAL = "postalCode";
  private static final String ACCEPTED = "privacyPolicyAccepted";
  private static final String DETAILS = "details";
  private static final String CONSENT_KEY = "consent";
  private static final String POLICY_KEY = "policyVersion";
  private static final String NOTE = "note";

  private static final String REQUIRED = "required";
  private static final String FORMAT = "format";
  private static final String EMAIL = "contact.email";

  private String checkout;

  @BeforeEach
  void startCheckout() {
    seedCatalog();
    checkout = checkoutId(startFromCart(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"name", EMAIL, PHONE, "street", CITY, COUNTRY, POSTAL})
  void everyMandatoryFieldOmittedIsRefusedByName(String field) {
    final Called called = submit(checkout, null, without(validForm(freshEmail()), field));

    assertEquals(400, called.status());
    assertEquals("validation.failed", called.body().get("code"));
    assertEquals(REQUIRED, violationCode(called, field));
    assertEquals(1, violations(called).size(), "only the omitted field is wrong");
  }

  @ParameterizedTest
  @ValueSource(strings = {"name", "street", CITY, POSTAL})
  void blankMandatoryFieldIsAlsoMissing(String field) {
    final Called called = submit(checkout, null, with(validForm(freshEmail()), field, "   "));

    assertEquals(REQUIRED, violationCode(called, field));
  }

  @Test
  void malformedEmailPhoneAndPostalCodeAreFormatViolationsNotMissingOnes() {
    final Map<String, Object> form = validForm("not-an-email");
    with(form, PHONE, "call me maybe");
    with(form, POSTAL, "!!");

    final Called called = submit(checkout, null, form);

    assertEquals(400, called.status());
    assertEquals(FORMAT, violationCode(called, EMAIL));
    assertEquals(FORMAT, violationCode(called, PHONE));
    assertEquals(FORMAT, violationCode(called, POSTAL));
  }

  @Test
  void requestWithTwoViolationsReturnsBoth() {
    final Map<String, Object> form = without(validForm(freshEmail()), CITY);
    with(form, EMAIL, "nope");

    final Called called = submit(checkout, null, form);

    assertEquals(2, violations(called).size());
    assertEquals(REQUIRED, violationCode(called, CITY));
    assertEquals(FORMAT, violationCode(called, EMAIL));
  }

  @Test
  void emptyBodyNamesEveryMandatoryFieldAndTheConsent() {
    final Called called = submit(checkout, null, Map.of());

    assertEquals(400, called.status());
    for (final String field :
        List.of("name", EMAIL, PHONE, "street", CITY, COUNTRY, POSTAL, ACCEPTED)) {
      assertNotNull(violationCode(called, field), field);
    }
  }

  @Test
  void theErrorEnvelopeKeepsItsShape() {
    final Called called = submit(checkout, null, without(validForm(freshEmail()), CITY));

    assertNotNull(called.body().get("correlationId"));
    assertNotNull(called.body().get("message"));
    assertEquals(CITY, violations(called).get(0).get("field"));
    assertNotNull(violations(called).get(0).get("message"));
  }

  @Test
  void privacyAcceptanceAbsentOrFalseIsRefusedAndTheSessionDoesNotAdvance() {
    final Called absent = submit(checkout, null, without(validForm(freshEmail()), ACCEPTED));
    final Called refused = submit(checkout, null, with(validForm(freshEmail()), ACCEPTED, false));

    assertEquals(400, absent.status());
    assertEquals("consent.privacy-required", violationCode(absent, ACCEPTED));
    assertEquals(400, refused.status());
    assertEquals("consent.privacy-required", violationCode(refused, ACCEPTED));
    final Called stored = session(checkout, null);
    assertNull(stored.body().get(DETAILS), "the session must not advance");
    assertNull(stored.body().get(CONSENT_KEY));
  }

  @Test
  void acceptanceOfAnotherVersionOrWithNoVersionIsRefused() {
    final Called stale =
        submit(checkout, null, with(validForm(freshEmail()), POLICY_KEY, "2019-01"));
    final Called none = submit(checkout, null, without(validForm(freshEmail()), POLICY_KEY));

    assertEquals("consent.version-mismatch", violationCode(stale, POLICY_KEY));
    assertEquals(REQUIRED, violationCode(none, POLICY_KEY));
  }

  @Test
  void bundledConsentFieldIsNotConsent() {
    final Map<String, Object> form = without(validForm(freshEmail()), ACCEPTED);
    form.put("termsAccepted", true);
    form.put("agreed", true);
    form.put(CONSENT_KEY, true);

    final Called called = submit(checkout, null, form);

    assertEquals("consent.privacy-required", violationCode(called, ACCEPTED));
  }

  @Test
  void theRequestHasNoFieldThatStandsForSeveralConsents() {
    final List<String> names =
        Arrays.stream(Step1Request.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    for (final String forbidden : List.of("termsAccepted", "agreed", "accepted", CONSENT_KEY)) {
      assertTrue(!names.contains(forbidden), forbidden);
    }
    assertTrue(names.contains(ACCEPTED));
    assertEquals(
        Boolean.class,
        Arrays.stream(Step1Request.class.getRecordComponents())
            .filter(component -> ACCEPTED.equals(component.getName()))
            .findFirst()
            .orElseThrow()
            .getType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"XX", "Germany", "ZZ", "1"})
  void countryThatIsNotCountryIsFieldErrorNotServerError(String country) {
    final Called called = submit(checkout, null, with(validForm(freshEmail()), COUNTRY, country));

    assertEquals(400, called.status());
    assertEquals("unsupported", violationCode(called, COUNTRY));
    assertTrue(
        violations(called).get(0).get("message").toString().contains("Delivery is not available"));
  }

  @Test
  void validStepOneIsRecordedNormalisedAndEchoed() {
    final String email = "Ann." + freshEmail().toUpperCase(java.util.Locale.ROOT);
    final Map<String, Object> form = validForm(email);
    with(form, "company.name", "Example GmbH");
    with(form, "company.address", "2 Side Street, Berlin");
    form.put(NOTE, "Ring twice");

    final Called called = submit(checkout, null, form);

    assertEquals(200, called.status());
    assertEquals(true, called.body().get("step1Complete"));
    final Map<?, ?> details = (Map<?, ?>) called.body().get(DETAILS);
    assertEquals(email.toLowerCase(java.util.Locale.ROOT), details.get("email"));
    assertEquals("+49301234567", details.get("phone"));
    assertEquals("DE", details.get(COUNTRY));
    assertEquals("Example GmbH", details.get("companyName"));
    assertEquals("Ring twice", details.get(NOTE));
    final Map<?, ?> consent = (Map<?, ?>) called.body().get(CONSENT_KEY);
    assertEquals(POLICY, consent.get(POLICY_KEY));
    assertNotNull(consent.get("acceptedAt"));
    assertNull(called.body().get("conversion"));
    assertEquals(details, session(checkout, null).body().get(DETAILS));
  }

  @Test
  void goingBackAndSubmittingAgainReplacesTheData() {
    submit(checkout, null, validForm(freshEmail()));

    final Called edited = submit(checkout, null, with(validForm(freshEmail()), CITY, "Hamburg"));

    assertEquals(200, edited.status());
    assertEquals("Hamburg", ((Map<?, ?>) session(checkout, null).body().get(DETAILS)).get(CITY));
  }

  @Test
  void overlongOptionalFieldIsRefused() {
    final Called called =
        submit(checkout, null, with(validForm(freshEmail()), NOTE, "x".repeat(501)));

    assertEquals("too-long", violationCode(called, NOTE));
  }

  @Test
  void unknownSessionIs404AndMalformedIdIs400() {
    assertEquals(404, submit("no-such-session", null, validForm(freshEmail())).status());
    assertEquals(400, submit("bad.id", null, validForm(freshEmail())).status());
  }
}
