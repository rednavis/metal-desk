package com.rednavis.metaldesk.api.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.api.account.AccountCreation;
import com.rednavis.metaldesk.api.account.verification.VerificationCodes;
import com.rednavis.metaldesk.api.account.verification.VerificationOutcome;
import com.rednavis.metaldesk.api.account.verification.VerificationPurpose;
import com.rednavis.metaldesk.api.account.verification.VerificationService;
import com.rednavis.metaldesk.api.account.verification.VerificationTicket;
import com.rednavis.metaldesk.api.checkout.step1.CustomerDetails;
import com.rednavis.metaldesk.api.checkout.step1.GuestConversion;
import com.rednavis.metaldesk.api.checkout.step1.GuestConversionService;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.AddressKind;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.PhoneNumber;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

/**
 * The guest conversion issues and confirms nothing itself: with the verification service stubbed,
 * every step is seen to be delegated to it (BRD FR-4.2, FR-2.6).
 */
class GuestConversionServiceTest {

  private static final String CUSTOMER_ID = "c-1";
  private static final String NAME = "Guest";
  private static final String NEW_PASSWORD = "a-good-password";
  private static final String REF_1 = "ref-1";
  private static final String HASH = "$2a$04$hash";

  private final VerificationService verification = Mockito.mock(VerificationService.class);
  private final AccountCreation accounts = Mockito.mock(AccountCreation.class);
  private final GuestConversionService service =
      new GuestConversionService(verification, accounts, new VerificationCodes());

  private final EmailAddress email = new EmailAddress("guest@example.com");
  private final CustomerDetails details =
      new CustomerDetails(
          NAME,
          email,
          new PhoneNumber("+491701234567"),
          new Address(
              AddressKind.DELIVERY,
              "1 Main Street",
              "Berlin",
              new Region("DE"),
              "10115",
              null,
              null),
          Optional.empty());

  private static CustomerDocument customer(VerificationState state) {
    return new CustomerDocument(CUSTOMER_ID, NAME, "guest@example.com", null, List.of(), state);
  }

  @Test
  void newGuestGetsAnUnverifiedAccountAndQuickRegistrationChallengeFromTheSharedService() {
    Mockito.when(accounts.hashPassword(NEW_PASSWORD)).thenReturn(Mono.just(HASH));
    Mockito.when(accounts.find(email)).thenReturn(Mono.just(Optional.empty()));
    Mockito.when(accounts.create(NAME, email, details.phone(), HASH))
        .thenReturn(Mono.just(customer(VerificationState.UNVERIFIED)));
    Mockito.when(
            verification.issue(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
        .thenReturn(Mono.just(new VerificationTicket(REF_1)));

    final GuestConversion started = service.start(details, NEW_PASSWORD, Locale.ENGLISH).block();

    assertEquals(REF_1, started.reference());
    assertEquals(CUSTOMER_ID, started.customer().orElseThrow().value());
    Mockito.verify(verification)
        .issue(
            ArgumentMatchers.eq(VerificationPurpose.CHECKOUT_QUICK_REGISTRATION),
            ArgumentMatchers.eq(CUSTOMER_ID),
            ArgumentMatchers.eq(email),
            ArgumentMatchers.eq(NAME),
            ArgumentMatchers.eq(Locale.ENGLISH));
  }

  @Test
  void addressThatAlreadyHasVerifiedAccountIssuesNothingAndAttachesNothing() {
    Mockito.when(accounts.hashPassword(NEW_PASSWORD)).thenReturn(Mono.just(HASH));
    Mockito.when(accounts.find(email))
        .thenReturn(Mono.just(Optional.of(customer(VerificationState.VERIFIED))));

    final GuestConversion started = service.start(details, NEW_PASSWORD, Locale.ENGLISH).block();

    assertEquals(Optional.empty(), started.customer());
    assertFalse(started.reference().isBlank());
    Mockito.verify(verification, Mockito.never())
        .issue(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
    Mockito.verify(accounts, Mockito.never())
        .create(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
  }

  @Test
  void unverifiedExistingAccountGetsFreshChallengeButIsNotAttachedToTheCheckout() {
    Mockito.when(accounts.hashPassword(NEW_PASSWORD)).thenReturn(Mono.just(HASH));
    Mockito.when(accounts.find(email))
        .thenReturn(Mono.just(Optional.of(customer(VerificationState.UNVERIFIED))));
    Mockito.when(
            verification.issue(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
        .thenReturn(Mono.just(new VerificationTicket("ref-2")));

    final GuestConversion started = service.start(details, NEW_PASSWORD, Locale.ENGLISH).block();

    assertEquals("ref-2", started.reference());
    assertEquals(Optional.empty(), started.customer(), "someone else account must not be attached");
    Mockito.verify(accounts, Mockito.never())
        .create(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
  }

  @Test
  void confirmationIsDelegatedToTheSharedServiceAndThenMarksTheAccountVerified() {
    Mockito.when(
            verification.confirm(REF_1, "123456", VerificationPurpose.CHECKOUT_QUICK_REGISTRATION))
        .thenReturn(
            Mono.just(
                new VerificationOutcome.Confirmed(
                    VerificationPurpose.CHECKOUT_QUICK_REGISTRATION,
                    CUSTOMER_ID,
                    "guest@example.com")));
    Mockito.when(accounts.markVerified(CUSTOMER_ID)).thenReturn(Mono.empty());

    service.confirm(REF_1, "123456").block();

    Mockito.verify(verification)
        .confirm(REF_1, "123456", VerificationPurpose.CHECKOUT_QUICK_REGISTRATION);
    Mockito.verify(accounts).markVerified(CUSTOMER_ID);
  }

  @Test
  void failedConfirmationMarksNothingVerified() {
    Mockito.when(
            verification.confirm(REF_1, "000000", VerificationPurpose.CHECKOUT_QUICK_REGISTRATION))
        .thenReturn(Mono.just(new VerificationOutcome.Failed()));

    final ValidationException failure =
        assertThrows(ValidationException.class, () -> service.confirm(REF_1, "000000").block());

    assertEquals("verification.invalid", failure.code());
    Mockito.verify(accounts, Mockito.never()).markVerified(ArgumentMatchers.any());
  }
}
