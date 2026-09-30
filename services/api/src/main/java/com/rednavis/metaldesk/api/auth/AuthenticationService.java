package com.rednavis.metaldesk.api.auth;

import com.rednavis.metaldesk.api.persistence.document.CredentialDocument;
import com.rednavis.metaldesk.api.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.api.persistence.repository.CredentialRepository;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.share.domain.customer.AuthIdentifier;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Signs a customer in (BRD FR-2.1, FR-2.2): finds the credential, checks the password, issues the
 * token.
 *
 * <p><strong>One failure path.</strong> However an attempt fails — no identifier, a malformed one,
 * one nobody owns, a customer without a credential, a disabled credential, a wrong password — it
 * becomes the single {@link SignInOutcome.Rejected}, and the controller builds the response from
 * that alone. The response therefore cannot differ by cause.
 *
 * <p><strong>Constant-ish timing.</strong> When there is no real credential to check, the password
 * is still verified against {@link PasswordEncoderAdapter#dummyHash()}, so "no such account" costs
 * about as much as "wrong password" and the response time does not reveal which accounts exist.
 *
 * <p>Verification state does not gate sign-in (BRD FR-2.3): an unverified customer signs in and
 * browses, and the state travels in the token for checkout to read.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private final CustomerRepository customers;
  private final CredentialRepository credentials;
  private final PasswordEncoderAdapter encoder;
  private final SignInThrottle throttle;
  private final JwtIssuer issuer;

  /**
   * Attempts a sign-in.
   *
   * @param request the submitted identifier and password; either may be null
   * @param source the throttle key of where the attempt came from
   * @return the outcome; never an error signal for a bad credential
   */
  public Mono<SignInOutcome> signIn(SignInRequest request, String source) {
    final Optional<Duration> locked = throttle.lockedFor(source);
    return locked.isPresent()
        ? Mono.just(new SignInOutcome.Throttled(locked.get()))
        : authenticate(request).map(found -> conclude(found, source));
  }

  private SignInOutcome conclude(Optional<AuthenticatedCustomer> found, String source) {
    found.ifPresentOrElse(
        customer -> throttle.recordSuccess(source), () -> throttle.recordFailure(source));
    return found
        .<SignInOutcome>map(customer -> new SignInOutcome.Success(issuer.issue(customer)))
        .orElseGet(SignInOutcome.Rejected::new);
  }

  private Mono<Optional<AuthenticatedCustomer>> authenticate(SignInRequest request) {
    final String identifier = request == null ? null : request.identifier();
    final String password = request == null ? null : request.password();
    return findCandidate(identifier)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(candidate -> verify(password, candidate));
  }

  private Mono<Optional<AuthenticatedCustomer>> verify(
      String password, Optional<Candidate> candidate) {
    final String hash =
        candidate.map(found -> found.credential().passwordHash()).orElse(encoder.dummyHash());
    final boolean usable =
        candidate.map(found -> found.credential().state().canSignIn()).orElse(false);
    return encoder
        .matches(password, hash)
        .map(
            matched ->
                matched && usable
                    ? candidate.map(Candidate::customer)
                    : Optional.<AuthenticatedCustomer>empty());
  }

  private Mono<Candidate> findCandidate(String identifier) {
    return Mono.defer(() -> lookUp(identifier))
        .flatMap(
            customer ->
                credentials
                    .findById(customer.id())
                    .map(credential -> new Candidate(principal(customer), credential)));
  }

  private Mono<CustomerDocument> lookUp(String identifier) {
    return parse(identifier)
        .map(
            parsed ->
                switch (parsed) {
                  case AuthIdentifier.Email email -> customers.findByEmail(email.normalised());
                  case AuthIdentifier.Phone phone -> customers.findFirstByPhone(phone.normalised());
                })
        .orElseGet(Mono::empty);
  }

  private static Optional<AuthIdentifier> parse(String identifier) {
    Optional<AuthIdentifier> parsed = Optional.empty();
    if (identifier != null) {
      try {
        parsed = Optional.of(AuthIdentifier.parse(identifier));
      } catch (ValidationException notAnIdentifier) {
        parsed = Optional.empty();
      }
    }
    return parsed;
  }

  private static AuthenticatedCustomer principal(CustomerDocument customer) {
    return new AuthenticatedCustomer(new CustomerId(customer.id()), customer.verification());
  }

  /** A customer and the credential to check the password against. */
  private record Candidate(AuthenticatedCustomer customer, CredentialDocument credential) {}
}
