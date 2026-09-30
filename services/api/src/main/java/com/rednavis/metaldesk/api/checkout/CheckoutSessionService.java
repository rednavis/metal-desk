package com.rednavis.metaldesk.api.checkout;

import com.rednavis.metaldesk.api.account.LocaleParser;
import com.rednavis.metaldesk.api.account.VerificationFailure;
import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.cart.BasketService;
import com.rednavis.metaldesk.api.cart.CheckoutBasket;
import com.rednavis.metaldesk.api.checkout.dto.ConfirmEmailRequest;
import com.rednavis.metaldesk.api.checkout.dto.SessionView;
import com.rednavis.metaldesk.api.checkout.dto.Step1Request;
import com.rednavis.metaldesk.api.checkout.dto.Step1Response;
import com.rednavis.metaldesk.api.checkout.payment.PaymentEditPolicy;
import com.rednavis.metaldesk.api.checkout.step1.ConversionState;
import com.rednavis.metaldesk.api.checkout.step1.GuestConversionService;
import com.rednavis.metaldesk.api.checkout.step1.Step1Validator;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.api.web.RequestIds;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Creates, reads and advances a checkout session, and runs step 1 (BRD FR-4.1 to FR-4.3).
 *
 * <p>Who may touch a session: one started by a signed-in customer belongs to that customer alone
 * (anyone else gets the same 404 as for a session that does not exist); one started by a guest is
 * reachable by whoever holds its id, an unguessable random reference.
 *
 * <p><strong>The verified-account gate (BRD FR-2.3).</strong> A signed-in customer whose email is
 * not verified cannot start or advance a checkout: 409 {@code checkout.account-unverified}. The
 * token's verification claim is the fast path, but a claim can be stale (the customer may have
 * verified since signing in), so when it says unverified the customer record is consulted, and a
 * verified record lets them through.
 *
 * <p>Going back and submitting step 1 again simply replaces what it holds (BRD FR-7.1).
 */
@Service
@RequiredArgsConstructor
public class CheckoutSessionService {

  private final CheckoutSessionStore store;
  private final BasketService baskets;
  private final GuestConversionService guests;
  private final CustomerRepository customers;
  private final CheckoutProperties properties;
  private final PaymentEditPolicy paymentEdits;
  private final Clock clock;

  /**
   * Starts a checkout from the cart or from a buy-now.
   *
   * @param cartReference the cart reference from the cookie, or null
   * @param customer the signed-in customer, or null for a guest
   * @param buyNowProductId the product to buy now, or null to check out the cart
   * @return the new session
   * @throws ConflictException {@code checkout.account-unverified}, or {@code
   *     checkout.basket-unpriced} if a line has no price at the moment
   * @throws ValidationException {@code checkout.basket-empty} if there is nothing to buy
   */
  public Mono<SessionView> start(
      String cartReference, AuthenticatedCustomer customer, String buyNowProductId) {
    final Mono<CheckoutBasket> basket =
        buyNowProductId == null
            ? baskets.basketFor(cartReference, customer)
            : baskets.buyNow(buyNowProductId);
    return requireVerified(customer)
        .then(basket)
        .flatMap(this::requireCheckoutable)
        .flatMap(
            found ->
                store.create(
                    Optional.ofNullable(customer).map(AuthenticatedCustomer::id),
                    found.source() == CheckoutBasket.Source.CART
                        ? CheckoutSession.Source.CART
                        : CheckoutSession.Source.BUY_NOW,
                    found.cart(),
                    found.lines()))
        .map(CheckoutViews::session);
  }

  /**
   * Reads a session.
   *
   * @param id the session's id
   * @param customer the signed-in customer, or null
   * @return the session
   * @throws NotFoundException {@code checkout.not-found}
   */
  public Mono<SessionView> get(String id, AuthenticatedCustomer customer) {
    return find(id, customer).map(CheckoutViews::session);
  }

  /**
   * Submits step 1: validates every field, records the privacy acceptance and, if a guest asked for
   * an account, starts their quick registration. Nothing is stored and no account is created unless
   * everything is valid.
   *
   * @param id the session's id
   * @param customer the signed-in customer, or null for a guest
   * @param request the submitted data
   * @return the recorded step 1
   * @throws com.rednavis.metaldesk.api.web.FieldViolationsException listing every invalid field
   * @throws ConflictException {@code checkout.account-unverified}
   */
  public Mono<Step1Response> submitStep1(
      String id, AuthenticatedCustomer customer, Step1Request request) {
    final boolean guest = customer == null;
    return find(id, customer)
        .flatMap(
            session ->
                requireVerified(customer)
                    .then(Mono.defer(() -> requireNotHandedOff(session)))
                    .then(Mono.defer(() -> paymentEdits.beforeEdit(session)))
                    .then(Mono.defer(() -> advance(session, guest, request))))
        .map(CheckoutViews::step1);
  }

  /**
   * Confirms the emailed code of a guest's quick registration. Checkout does not wait for this.
   *
   * @param id the session's id
   * @param customer the signed-in customer, or null
   * @param request the reference from step 1 and the code
   * @return the session after the confirmation
   * @throws ValidationException the single {@code verification.invalid} for any failure
   */
  public Mono<SessionView> confirmEmail(
      String id, AuthenticatedCustomer customer, ConfirmEmailRequest request) {
    final String reference = request == null ? null : request.reference();
    final String code = request == null ? null : request.code();
    return find(id, customer)
        .filter(
            session ->
                session
                    .conversion()
                    .map(ConversionState::reference)
                    .filter(r -> r.equals(reference))
                    .isPresent())
        .switchIfEmpty(Mono.error(VerificationFailure.create()))
        .flatMap(session -> guests.confirm(reference, code).thenReturn(session))
        .flatMap(session -> store.update(id, CheckoutSession::withConversionVerified))
        .map(CheckoutViews::session);
  }

  private Mono<CheckoutSession> advance(
      CheckoutSession session, boolean guest, Step1Request request) {
    final Step1Validator.Validated valid =
        Step1Validator.validate(request, guest, clock.instant(), properties.policyVersion());
    final Locale locale = LocaleParser.parse(request == null ? null : request.locale());
    final Mono<Optional<ConversionState>> conversion =
        valid.password().isPresent() && !alreadyConverted(session, valid.details().email().value())
            ? guests
                .start(valid.details(), valid.password().get(), locale)
                .map(
                    started ->
                        Optional.of(
                            new ConversionState(
                                valid.details().email().value(),
                                started.reference(),
                                started.customer(),
                                false)))
            : Mono.just(session.conversion());
    return conversion.flatMap(
        state ->
            store.update(
                session.id(),
                current -> current.withStep1(valid.details(), valid.consent(), state)));
  }

  private static Mono<Void> requireNotHandedOff(CheckoutSession session) {
    return session.handoff().isPresent()
        ? Mono.error(
            new ConflictException(
                "checkout.already-handed-off",
                "This order was handed to our team; it can no longer be edited"))
        : Mono.empty();
  }

  private static boolean alreadyConverted(CheckoutSession session, String email) {
    return session.conversion().map(ConversionState::email).filter(email::equals).isPresent();
  }

  /**
   * Finds a session the caller may touch.
   *
   * @param id the session's id
   * @param customer the signed-in customer, or null for a guest
   * @return the session; a session that is not the customer's is the same 404 as a missing one
   * @throws NotFoundException {@code checkout.not-found}
   */
  public Mono<CheckoutSession> find(String id, AuthenticatedCustomer customer) {
    RequestIds.require(id, "checkout");
    return store
        .find(id)
        .filter(
            session ->
                session.owner().isEmpty()
                    || (customer != null && session.owner().get().equals(customer.id())))
        .switchIfEmpty(
            Mono.error(new NotFoundException("checkout.not-found", "No such checkout session")));
  }

  private Mono<CheckoutBasket> requireCheckoutable(CheckoutBasket basket) {
    Mono<CheckoutBasket> result = Mono.just(basket);
    if (basket.unpriced().isEmpty()) {
      if (basket.lines().isEmpty()) {
        result =
            Mono.error(new ValidationException("checkout.basket-empty", "There is nothing to buy"));
      }
    } else {
      result =
          Mono.error(
              new ConflictException(
                  "checkout.basket-unpriced",
                  "Some items in the cart have no price at the moment"));
    }
    return result;
  }

  /**
   * Applies the verified-account gate (BRD FR-2.3).
   *
   * @param customer the signed-in customer, or null for a guest
   * @return a signal that completes if they may check out
   * @throws ConflictException {@code checkout.account-unverified}
   */
  public Mono<Void> requireVerified(AuthenticatedCustomer customer) {
    Mono<Void> gate = Mono.empty();
    if (customer != null && customer.verification() != VerificationState.VERIFIED) {
      gate =
          customers
              .findById(customer.id().value())
              .filter(found -> found.verification() == VerificationState.VERIFIED)
              .switchIfEmpty(
                  Mono.error(
                      new ConflictException(
                          "checkout.account-unverified",
                          "Verify your email address before checking out")))
              .then();
    }
    return gate;
  }
}
