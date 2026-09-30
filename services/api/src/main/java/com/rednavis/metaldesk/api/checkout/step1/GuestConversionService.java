package com.rednavis.metaldesk.api.checkout.step1;

import com.rednavis.metaldesk.api.account.AccountCreation;
import com.rednavis.metaldesk.api.account.VerificationFailure;
import com.rednavis.metaldesk.api.account.verification.VerificationCodes;
import com.rednavis.metaldesk.api.account.verification.VerificationOutcome;
import com.rednavis.metaldesk.api.account.verification.VerificationPurpose;
import com.rednavis.metaldesk.api.account.verification.VerificationService;
import com.rednavis.metaldesk.api.account.verification.VerificationTicket;
import com.rednavis.metaldesk.api.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * A guest's "remember me" at checkout (BRD FR-4.2): turn the checkout into a lightweight
 * registration, verifying the email <strong>by reusing the FR-2.6 primitive</strong>.
 *
 * <p>This class issues and confirms nothing itself. The code, its hash, its lifetime, the attempt
 * limit and the mail all belong to {@link VerificationService}; this class asks it to {@link
 * VerificationService#issue issue} a {@link VerificationPurpose#CHECKOUT_QUICK_REGISTRATION}
 * challenge and to {@link VerificationService#confirm confirm} it, and creates the account through
 * {@link AccountCreation}, which registration uses too. There is one way to issue a verification to
 * an address, so there cannot be two that diverge.
 *
 * <p><strong>Two requirements that could be read as contradictory, and how they are read.</strong>
 * FR-4.2 says the account is created "without leaving the checkout flow"; FR-2.3 says an account is
 * not usable for checkout until its email is verified. They meet here and are reconciled by
 * <em>what is being gated</em>: FR-2.3 gates a verified-account customer's <em>later</em> checkouts
 * (a signed-in {@code UNVERIFIED} customer is refused), while FR-4.2 lets this guest finish
 * <em>this</em> checkout. So the account is created {@code UNVERIFIED}, checkout does not wait for
 * the confirmation, and the guest confirms the code whenever they like. The guest holds no token,
 * so the sign-in gate does not apply to them.
 *
 * <p><strong>No enumeration, no takeover.</strong> An address that already has an account gets the
 * same kind of answer as a new one and nothing is changed: an unverified account is sent a fresh
 * code (only its owner can read it), a verified one gets a reference that never confirms. The
 * account is attached to the checkout only when this guest created it, so typing someone else's
 * address cannot attribute an order to their account.
 */
@Service
@RequiredArgsConstructor
public class GuestConversionService {

  private final VerificationService verification;
  private final AccountCreation accounts;
  private final VerificationCodes references;

  /**
   * Starts a guest's quick registration.
   *
   * @param details the validated step-1 data
   * @param password the guest's chosen password, already checked against the password policy
   * @param locale the language of the verification mail
   * @return the reference to quote when confirming, and the account if this guest created one
   */
  public Mono<GuestConversion> start(CustomerDetails details, String password, Locale locale) {
    final EmailAddress email = details.email();
    return accounts
        .hashPassword(password)
        .flatMap(
            hash ->
                accounts
                    .find(email)
                    .flatMap(
                        existing ->
                            existing.isPresent()
                                ? existingAccount(existing.get(), email, locale)
                                : create(details, hash, locale)));
  }

  /**
   * Confirms the emailed code and marks the new account verified.
   *
   * @param reference the verification reference from step 1
   * @param code the code from the email
   * @return a signal that completes when the account is verified; the single {@code
   *     verification.invalid} error for any failure
   */
  public Mono<Void> confirm(String reference, String code) {
    return verification
        .confirm(reference, code, VerificationPurpose.CHECKOUT_QUICK_REGISTRATION)
        .flatMap(
            outcome ->
                switch (outcome) {
                  case VerificationOutcome.Confirmed confirmed ->
                      accounts.markVerified(confirmed.subject());
                  case VerificationOutcome.Failed() -> Mono.error(VerificationFailure.create());
                });
  }

  private Mono<GuestConversion> create(CustomerDetails details, String hash, Locale locale) {
    return accounts
        .create(details.name(), details.email(), details.phone(), hash)
        .flatMap(
            customer ->
                issue(customer.id(), details.email(), customer.name(), locale)
                    .map(
                        ticket ->
                            new GuestConversion(
                                ticket.reference(), Optional.of(new CustomerId(customer.id())))))
        // A registration of the same address won the unique index in the meantime.
        .onErrorResume(DuplicateKeyException.class, race -> Mono.just(inert()));
  }

  private Mono<GuestConversion> existingAccount(
      CustomerDocument existing, EmailAddress email, Locale locale) {
    return existing.verification() == VerificationState.UNVERIFIED
        ? issue(existing.id(), email, existing.name(), locale)
            .map(ticket -> new GuestConversion(ticket.reference(), Optional.empty()))
        : Mono.just(inert());
  }

  private Mono<VerificationTicket> issue(
      String subject, EmailAddress email, String name, Locale locale) {
    return verification.issue(
        VerificationPurpose.CHECKOUT_QUICK_REGISTRATION, subject, email, name, locale);
  }

  private GuestConversion inert() {
    return new GuestConversion(references.reference(), Optional.empty());
  }
}
