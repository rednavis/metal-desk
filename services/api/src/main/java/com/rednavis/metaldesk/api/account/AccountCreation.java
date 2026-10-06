package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.api.auth.PasswordEncoderAdapter;
import com.rednavis.metaldesk.api.persistence.repository.CredentialRepository;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.persistence.document.CredentialDocument;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.customer.AuthCredential;
import com.rednavis.metaldesk.share.domain.customer.Customer;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.PhoneNumber;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The creation of an account and the marking of its email as verified, shared by every flow that
 * makes one: registration (BRD FR-2.3) and the quick registration at checkout (FR-4.2).
 *
 * <p>It does the account side only. What verifies the email (the {@code VerificationService}
 * primitive of FR-2.6), which purpose it is issued for, and what to say to an address that already
 * has an account are the caller's, so the flows differ only in those and not in how a customer or
 * credential is written.
 */
@Component
@RequiredArgsConstructor
public class AccountCreation {

  private final CustomerRepository customers;
  private final CredentialRepository credentials;
  private final PasswordEncoderAdapter encoder;

  /**
   * Hashes a new password off the event loop.
   *
   * @param password the plaintext password, already checked against {@link PasswordPolicy}
   * @return the encoded hash
   */
  public Mono<String> hashPassword(String password) {
    return encoder.encode(password);
  }

  /**
   * Looks an account up by email address.
   *
   * @param email the address
   * @return the customer, if there is one
   */
  public Mono<Optional<CustomerDocument>> find(EmailAddress email) {
    return customers.findByEmail(email.value()).map(Optional::of).defaultIfEmpty(Optional.empty());
  }

  /**
   * Creates an {@code UNVERIFIED} customer and an active credential.
   *
   * @param name the customer's name
   * @param email the email address
   * @param phone the phone number, or null
   * @param hash the encoded password hash
   * @return the new customer; an error signal ({@code DuplicateKeyException}) if the address was
   *     registered in the meantime
   * @throws ValidationException if the name is blank
   */
  public Mono<CustomerDocument> create(
      String name, EmailAddress email, PhoneNumber phone, String hash) {
    final Customer customer =
        new Customer(
            new CustomerId(UUID.randomUUID().toString()),
            name,
            email,
            phone,
            List.of(),
            VerificationState.UNVERIFIED);
    final CustomerDocument document =
        new CustomerDocument(
            customer.id().value(),
            customer.name(),
            email.value(),
            phone == null ? null : phone.value(),
            List.of(),
            customer.verification());
    return customers
        .save(document)
        .flatMap(
            saved ->
                credentials
                    .save(new CredentialDocument(saved.id(), hash, AuthCredential.State.ACTIVE))
                    .thenReturn(saved));
  }

  /**
   * Creates an {@code UNVERIFIED} customer with no credential: the contact record of a guest whose
   * order needs a customer to belong to. It cannot sign in. The person can claim it later by
   * verifying the address and choosing a password through a password reset, which creates the
   * credential once they have proved they own the mailbox.
   *
   * @param name the customer's name
   * @param email the email address
   * @param phone the phone number
   * @return the new customer; an error signal ({@code DuplicateKeyException}) if the address is
   *     registered in the meantime
   */
  public Mono<CustomerDocument> createGuest(String name, EmailAddress email, PhoneNumber phone) {
    return customers.save(
        new CustomerDocument(
            UUID.randomUUID().toString(),
            name,
            email.value(),
            phone.value(),
            List.of(),
            VerificationState.UNVERIFIED));
  }

  /**
   * Marks a customer's email verified.
   *
   * @param customerId the customer's id
   * @return a signal that completes when it is saved; the single {@code verification.invalid} error
   *     if there is no such customer
   */
  public Mono<Void> markVerified(String customerId) {
    return customers
        .findById(customerId)
        .switchIfEmpty(Mono.error(VerificationFailure.create()))
        .flatMap(
            found ->
                customers.save(
                    new CustomerDocument(
                        found.id(),
                        found.name(),
                        found.email(),
                        found.phone(),
                        found.addresses(),
                        VerificationState.VERIFIED)))
        .then();
  }
}
