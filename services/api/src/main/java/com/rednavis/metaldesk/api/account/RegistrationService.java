package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.api.account.dto.ConfirmEmailRequest;
import com.rednavis.metaldesk.api.account.dto.RegistrationAccepted;
import com.rednavis.metaldesk.api.account.dto.RegistrationRequest;
import com.rednavis.metaldesk.api.account.verification.VerificationOutcome;
import com.rednavis.metaldesk.api.account.verification.VerificationPurpose;
import com.rednavis.metaldesk.api.account.verification.VerificationService;
import com.rednavis.metaldesk.api.account.verification.VerificationTicket;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.PhoneNumber;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Registration and email confirmation (BRD FR-2.3, FR-2.6).
 *
 * <p>Registration creates an {@code UNVERIFIED} customer and a credential, sends a verification
 * code through {@link VerificationService}, and <strong>does not sign the customer in</strong>: no
 * token is issued at this point. The customer confirms the email, then signs in, and the token they
 * get says {@code VERIFIED}. That keeps an unverified account from ever holding a token the
 * checkout gate (FR-2.3, T-035) could mistake for a verified one.
 *
 * <p><strong>No enumeration.</strong> Registering an address that already has an account gets the
 * same response as a new one, with a reference in the same shape. For an unverified account a fresh
 * code is mailed, so a user who lost the first can ask again; for a verified one nothing is sent
 * and the reference is a dummy that will never confirm. The existing account is never changed, and
 * the password is hashed on every path so the timing does not tell them apart.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

  private static final String ACCEPTED = "Check your email for a verification code";
  private static final int REFERENCE_BYTES = 16;

  private final AccountCreation accounts;
  private final VerificationService verification;
  private final SecureRandom random = new SecureRandom();

  /**
   * Registers a customer.
   *
   * @param request the profile and password
   * @return the acceptance, always the same shape
   * @throws ValidationException if the profile or password is invalid
   */
  public Mono<RegistrationAccepted> register(RegistrationRequest request) {
    if (request == null) {
      throw new ValidationException("registration.invalid", "A registration is required");
    }
    final String name = requireName(request.name());
    final EmailAddress email = new EmailAddress(request.email());
    final PhoneNumber phone =
        request.phone() == null || request.phone().isBlank()
            ? null
            : new PhoneNumber(request.phone());
    final String password = PasswordPolicy.require(request.password());
    final Locale locale = LocaleParser.parse(request.locale());
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
                                : create(name, email, phone, hash, locale)))
        .map(reference -> new RegistrationAccepted(reference, ACCEPTED));
  }

  /**
   * Confirms the email address of a registration and marks the customer verified.
   *
   * @param request the reference and code
   * @return a signal that completes when the customer is verified
   * @throws ValidationException with the single code {@code verification.invalid} for any failure
   */
  public Mono<Void> confirmEmail(ConfirmEmailRequest request) {
    final String reference = request == null ? null : request.reference();
    final String code = request == null ? null : request.code();
    return verification
        .confirm(reference, code, VerificationPurpose.REGISTRATION)
        .flatMap(
            outcome ->
                switch (outcome) {
                  case VerificationOutcome.Confirmed confirmed ->
                      accounts.markVerified(confirmed.subject());
                  case VerificationOutcome.Failed() -> Mono.error(VerificationFailure.create());
                });
  }

  private Mono<String> create(
      String name, EmailAddress email, PhoneNumber phone, String hash, Locale locale) {
    return accounts
        .create(name, email, phone, hash)
        .flatMap(
            customer ->
                verification.issue(
                    VerificationPurpose.REGISTRATION,
                    customer.id(),
                    email,
                    customer.name(),
                    locale))
        .map(VerificationTicket::reference)
        // A concurrent registration of the same address won the unique index: treat as existing.
        .onErrorResume(DuplicateKeyException.class, race -> Mono.just(dummyReference()));
  }

  private Mono<String> existingAccount(
      CustomerDocument existing, EmailAddress email, Locale locale) {
    return existing.verification() == VerificationState.UNVERIFIED
        ? verification
            .issue(VerificationPurpose.REGISTRATION, existing.id(), email, existing.name(), locale)
            .map(VerificationTicket::reference)
        : Mono.just(dummyReference());
  }

  private String dummyReference() {
    final byte[] bytes = new byte[REFERENCE_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new ValidationException("registration.name-blank", "Name must not be blank");
    }
    return name.strip();
  }
}
