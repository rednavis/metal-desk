package com.rednavis.metaldesk.api.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.rednavis.metaldesk.api.account.MailInspector;
import com.rednavis.metaldesk.api.persistence.document.CheckoutSessionDocument;
import com.rednavis.metaldesk.api.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.api.persistence.repository.VerificationChallengeRepository;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.HttpMethod;

/** "Remember me" at checkout (BRD FR-4.2): a quick registration that reuses FR-2.6. */
class CheckoutGuestConversionTest extends CheckoutTestSupport {

  private static final String ACCOUNT = "account.password";
  private static final String REFERENCE = "reference";
  private static final String CODE = "code";
  private static final String CONVERSION = "conversion";
  private static final String PASSWORD_WORD = "password";
  private static final String CONFIRM_PATH = "/confirm-email";

  @Autowired private CustomerRepository customers;
  @Autowired private VerificationChallengeRepository challenges;
  @Autowired private ReactiveMongoTemplate mongo;

  private String checkout;

  @BeforeEach
  void startCheckout() {
    seedCatalog();
    checkout = checkoutId(startFromCart(null));
  }

  private Map<String, Object> rememberMe(String email) {
    final Map<String, Object> form = validForm(email);
    with(form, "account.rememberMe", true);
    with(form, ACCOUNT, PASSWORD);
    return form;
  }

  private CustomerDocument customer(String email) {
    return Objects.requireNonNull(customers.findByEmail(email).block());
  }

  private Called confirm(String reference, String code) {
    return call(
        HttpMethod.POST,
        SESSIONS + "/" + checkout + CONFIRM_PATH,
        null,
        null,
        Map.of(REFERENCE, reference, CODE, code));
  }

  @Test
  void rememberMeCreatesAnUnverifiedAccountAndOneQuickRegistrationChallengeAndMail() {
    final String email = freshEmail();

    final Called called = submit(checkout, null, rememberMe(email));

    assertEquals(200, called.status());
    final Map<?, ?> conversion = (Map<?, ?>) called.body().get(CONVERSION);
    final String reference = (String) conversion.get(REFERENCE);
    assertNotNull(reference);
    assertEquals(false, conversion.get("verified"));
    assertEquals(VerificationState.UNVERIFIED, customer(email).verification());
    assertEquals(1, mailTo(email).size(), "exactly one mail");
    assertEquals(MailTemplate.EMAIL_VERIFICATION, mailTo(email).get(0).template());
    assertEquals(
        "CHECKOUT_QUICK_REGISTRATION",
        Objects.requireNonNull(challenges.findById(reference).block()).purpose());
  }

  @Test
  void checkoutDoesNotWaitForTheConfirmation() {
    final String email = freshEmail();

    final Called called = submit(checkout, null, rememberMe(email));

    assertEquals(true, called.body().get("step1Complete"));
    final Called stored = session(checkout, null);
    assertEquals(200, stored.status());
    assertNotNull(stored.body().get("details"), "step 1 is recorded before any confirmation");
    assertEquals(VerificationState.UNVERIFIED, customer(email).verification());
  }

  @Test
  void theEmailedCodeConfirmsTheAccountAndTheGuestCanThenSignIn() {
    final String email = freshEmail();
    final Called submitted = submit(checkout, null, rememberMe(email));
    final String reference = (String) ((Map<?, ?>) submitted.body().get(CONVERSION)).get(REFERENCE);

    final Called confirmed = confirm(reference, MailInspector.typedCode(mailTo(email).get(0)));

    assertEquals(200, confirmed.status());
    assertEquals(true, ((Map<?, ?>) confirmed.body().get(CONVERSION)).get("verified"));
    assertEquals(VerificationState.VERIFIED, customer(email).verification());
    assertNotNull(signIn(email, PASSWORD));
  }

  @Test
  void wrongCodeAndAnotherSessionsReferenceFailTheSameWay() {
    final String email = freshEmail();
    final Called submitted = submit(checkout, null, rememberMe(email));
    final String reference = (String) ((Map<?, ?>) submitted.body().get(CONVERSION)).get(REFERENCE);

    final Called wrong = confirm(reference, "000000");
    final Called other =
        confirm("someone-elses-reference", MailInspector.typedCode(mailTo(email).get(0)));

    assertEquals(400, wrong.status());
    assertEquals("verification.invalid", wrong.body().get(CODE));
    assertEquals(wrong.status(), other.status());
    assertEquals(wrong.body().get(CODE), other.body().get(CODE));
    assertEquals(VerificationState.UNVERIFIED, customer(email).verification());
  }

  @Test
  void registrationPurposeCodeDoesNotConfirmQuickRegistration() {
    final String email = freshEmail();
    register(email);
    final String otherReference = (String) register(freshEmail()).body().get(REFERENCE);

    assertEquals(400, confirm(otherReference, "123456").status());
  }

  @Test
  void missingOrWeakPasswordIsFieldErrorAndNothingIsCreatedOrSent() {
    final String email = freshEmail();
    final Map<String, Object> missing = validForm(email);
    with(missing, "account.rememberMe", true);
    final Map<String, Object> weak = rememberMe(email);
    with(weak, ACCOUNT, "short");

    final Called noPassword = submit(checkout, null, missing);
    final Called weakPassword = submit(checkout, null, weak);

    assertEquals("required", violationCode(noPassword, ACCOUNT));
    assertEquals("password.invalid", violationCode(weakPassword, ACCOUNT));
    assertNull(customers.findByEmail(email).block());
    assertEquals(0, mailTo(email).size());
  }

  @Test
  void invalidFormCreatesNoAccountEvenWithRememberMe() {
    final String email = freshEmail();

    submit(checkout, null, without(rememberMe(email), "city"));

    assertNull(customers.findByEmail(email).block());
    assertEquals(0, mailTo(email).size());
  }

  @Test
  void refusedPrivacyAcceptanceCreatesNoAccount() {
    final String email = freshEmail();

    submit(checkout, null, with(rememberMe(email), "privacyPolicyAccepted", false));

    assertNull(customers.findByEmail(email).block());
  }

  @Test
  void resubmittingWithTheSameAddressDoesNotSendAnotherMail() {
    final String email = freshEmail();
    final Called first = submit(checkout, null, rememberMe(email));

    final Called second = submit(checkout, null, with(rememberMe(email), "city", "Hamburg"));

    assertEquals(1, mailTo(email).size());
    assertEquals(
        ((Map<?, ?>) first.body().get(CONVERSION)).get(REFERENCE),
        ((Map<?, ?>) second.body().get(CONVERSION)).get(REFERENCE));
  }

  @Test
  void addressThatAlreadyHasVerifiedAccountLooksTheSameAndChangesNothing() {
    final String email = freshEmail();
    registerAndVerify(email);
    final int mailBefore = mailTo(email).size();

    final Called called = submit(checkout, null, rememberMe(email));

    assertEquals(200, called.status());
    final Map<?, ?> conversion = (Map<?, ?>) called.body().get(CONVERSION);
    assertNotNull(conversion.get(REFERENCE));
    assertEquals(false, conversion.get("verified"));
    assertEquals(mailBefore, mailTo(email).size(), "no mail to the account's owner");
    assertNotNull(signIn(email, PASSWORD), "the existing password is untouched");
    assertNull(signIn(email, "the-guests-attempt"));
  }

  @Test
  void existingUnverifiedAccountIsNotAttachedToTheGuestsCheckout() {
    final String email = freshEmail();
    register(email);

    final Called called = submit(checkout, null, rememberMe(email));

    assertEquals(200, called.status());
    final CheckoutSessionDocument stored =
        Objects.requireNonNull(mongo.findById(checkout, CheckoutSessionDocument.class).block());
    assertNull(
        stored.conversion().customerId(), "an order must not be attributed to their account");
    assertEquals(2, mailTo(email).size(), "the owner is sent a fresh code");
  }

  @Test
  void rememberMeFromSignedInCustomerIsIgnored() {
    final String token = signedInToken();
    final Called cart = add(null, token, GOLD_2);
    final String signedInCheckout =
        checkoutId(call(HttpMethod.POST, SESSIONS, cart.cookie(), token, null));
    final String email = freshEmail();

    final Called called = submit(signedInCheckout, token, rememberMe(email));

    assertEquals(200, called.status());
    assertNull(called.body().get(CONVERSION));
    assertNull(customers.findByEmail(email).block());
  }

  @Test
  void thePasswordIsNeverEchoedOrStoredOnTheSession() {
    final Called called = submit(checkout, null, rememberMe(freshEmail()));

    assertFalse(called.body().toString().contains(PASSWORD));
    assertFalse(
        Objects.requireNonNull(mongo.findById(checkout, CheckoutSessionDocument.class).block())
            .toString()
            .contains(PASSWORD));
    assertNull(called.body().get(PASSWORD_WORD));
  }
}
