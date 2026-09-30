package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.api.account.dto.PasswordResetConfirmation;
import com.rednavis.metaldesk.api.account.dto.PasswordResetRequest;
import com.rednavis.metaldesk.api.account.verification.VerificationChallengeStore;
import com.rednavis.metaldesk.api.account.verification.VerificationOutcome;
import com.rednavis.metaldesk.api.account.verification.VerificationPurpose;
import com.rednavis.metaldesk.api.account.verification.VerificationService;
import com.rednavis.metaldesk.api.auth.PasswordEncoderAdapter;
import com.rednavis.metaldesk.api.persistence.document.CredentialDocument;
import com.rednavis.metaldesk.api.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.api.persistence.repository.CredentialRepository;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Password reset by a time-limited emailed link (BRD FR-2.4).
 *
 * <p><strong>No oracle.</strong> {@link #request} completes the same way for an unknown address, an
 * unverified one and a known one, and only the last is mailed. To keep the timing close, the other
 * paths still do one password hash. What can still differ is the time the mail transport takes;
 * nothing here can hide that.
 *
 * <p><strong>What completing a reset does.</strong> It sets the new password and cancels every
 * other outstanding reset challenge for that customer, so an older link cannot be used later.
 * Tokens already issued cannot be revoked (they are stateless, and live at most their short
 * lifetime); that limit is recorded in the ledger.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

  private static final String DUMMY_SECRET = "no-such-account";

  private final CustomerRepository customers;
  private final CredentialRepository credentials;
  private final PasswordEncoderAdapter encoder;
  private final VerificationService verification;
  private final VerificationChallengeStore challenges;

  /**
   * Requests a reset link.
   *
   * @param request the account's email address and the mail language
   * @return a signal that completes the same way whether or not a mail was sent
   * @throws ValidationException if the address is not an email address at all
   */
  public Mono<Void> request(PasswordResetRequest request) {
    if (request == null) {
      throw new ValidationException("password-reset.invalid", "A reset request is required");
    }
    final EmailAddress email = new EmailAddress(request.email());
    final Locale locale = LocaleParser.parse(request.locale());
    return customers
        .findByEmail(email.value())
        .filter(customer -> customer.verification() == VerificationState.VERIFIED)
        .flatMap(customer -> issue(customer, email, locale))
        .switchIfEmpty(Mono.defer(() -> encoder.encode(DUMMY_SECRET).then()));
  }

  /**
   * Completes a reset.
   *
   * @param confirmation the link's reference and code, and the new password
   * @return a signal that completes once the password is changed
   * @throws ValidationException {@code password.invalid} for a weak password (checked before the
   *     code is spent), or the single {@code verification.invalid} for any failure to confirm
   */
  public Mono<Void> reset(PasswordResetConfirmation confirmation) {
    final String reference = confirmation == null ? null : confirmation.reference();
    final String code = confirmation == null ? null : confirmation.code();
    final String password =
        PasswordPolicy.require(confirmation == null ? null : confirmation.newPassword());
    return verification
        .confirm(reference, code, VerificationPurpose.PASSWORD_RESET)
        .flatMap(
            outcome ->
                switch (outcome) {
                  case VerificationOutcome.Confirmed confirmed ->
                      change(confirmed.subject(), password);
                  case VerificationOutcome.Failed() -> Mono.error(VerificationFailure.create());
                });
  }

  private Mono<Void> issue(CustomerDocument customer, EmailAddress email, Locale locale) {
    return verification
        .issue(VerificationPurpose.PASSWORD_RESET, customer.id(), email, customer.name(), locale)
        .then();
  }

  private Mono<Void> change(String customerId, String password) {
    return credentials
        .findById(customerId)
        .switchIfEmpty(Mono.error(VerificationFailure.create()))
        .flatMap(
            existing ->
                encoder
                    .encode(password)
                    .flatMap(
                        hash ->
                            credentials.save(
                                new CredentialDocument(existing.id(), hash, existing.state()))))
        .then(challenges.invalidatePending(VerificationPurpose.PASSWORD_RESET, customerId));
  }
}
