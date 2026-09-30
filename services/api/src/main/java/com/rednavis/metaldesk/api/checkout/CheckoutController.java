package com.rednavis.metaldesk.api.checkout;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.cart.CartController;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryEvaluationService;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryViews;
import com.rednavis.metaldesk.api.checkout.delivery.HandoffService;
import com.rednavis.metaldesk.api.checkout.delivery.dto.DeliveryEvaluationView;
import com.rednavis.metaldesk.api.checkout.delivery.dto.HandoffRequest;
import com.rednavis.metaldesk.api.checkout.delivery.dto.HandoffView;
import com.rednavis.metaldesk.api.checkout.dto.ConfirmEmailRequest;
import com.rednavis.metaldesk.api.checkout.dto.SessionView;
import com.rednavis.metaldesk.api.checkout.dto.StartRequest;
import com.rednavis.metaldesk.api.checkout.dto.Step1Request;
import com.rednavis.metaldesk.api.checkout.dto.Step1Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The checkout endpoints, starting with step 1 (BRD section 7.4); {@code T-036} to {@code T-038}
 * add the steps that follow. They are public, because a guest checks out: a signed-in customer is
 * recognised by their token, and a session is found by its id.
 */
@RestController
@RequestMapping("/api/checkout/sessions")
@RequiredArgsConstructor
public class CheckoutController {

  private final CheckoutSessionService service;
  private final DeliveryEvaluationService delivery;
  private final HandoffService handoffs;

  /**
   * Starts a checkout from the cart, or from a buy-now when a product is named.
   *
   * @param cartReference the cart cookie, if any
   * @param customer the signed-in customer, if any
   * @param request optionally names the product to buy now
   * @return 201 with the new session; 409 for an unverified account or an unpriced cart
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<SessionView> start(
      @CookieValue(name = CartController.COOKIE, required = false) String cartReference,
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @RequestBody(required = false) StartRequest request) {
    return service.start(
        cartReference, customer, request == null ? null : request.buyNowProductId());
  }

  /**
   * Reads a session, to show a step again.
   *
   * @param id the session's id
   * @param customer the signed-in customer, if any
   * @return the session
   */
  @GetMapping("/{id}")
  public Mono<SessionView> session(
      @PathVariable String id, @AuthenticationPrincipal AuthenticatedCustomer customer) {
    return service.get(id, customer);
  }

  /**
   * Submits step 1: customer and delivery data, privacy acceptance, and optionally a guest's
   * "remember me". Always the full field set; submitting again replaces it.
   *
   * @param id the session's id
   * @param customer the signed-in customer, if any
   * @param request the step-1 form
   * @return the recorded step 1; 400 listing every invalid field; 409 for an unverified account
   */
  @PutMapping("/{id}/step1")
  public Mono<Step1Response> step1(
      @PathVariable String id,
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @RequestBody(required = false) Step1Request request) {
    return service.submitStep1(id, customer, request);
  }

  /**
   * Confirms the emailed code of a guest's quick registration. Checkout does not wait for this.
   *
   * @param id the session's id
   * @param customer the signed-in customer, if any
   * @param request the reference from step 1 and the code
   * @return the session; 400 {@code verification.invalid} for every kind of failure
   */
  @PostMapping("/{id}/confirm-email")
  public Mono<SessionView> confirmEmail(
      @PathVariable String id,
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @RequestBody(required = false) ConfirmEmailRequest request) {
    return service.confirmEmail(id, customer, request);
  }

  /**
   * Evaluates the delivery against the tiers configured right now (BRD FR-5.1 to FR-5.3). The
   * result says whether the customer may pay or the order needs a manager, and why.
   *
   * @param id the session's id
   * @param customer the signed-in customer, if any
   * @return the evaluation; 409 before step 1 is complete
   */
  @PostMapping("/{id}/delivery/evaluate")
  public Mono<DeliveryEvaluationView> evaluateDelivery(
      @PathVariable String id, @AuthenticationPrincipal AuthenticatedCustomer customer) {
    return delivery
        .evaluate(id, customer)
        .map(session -> DeliveryViews.evaluation(session).orElseThrow());
  }

  /**
   * Hands the order to staff (BRD FR-5.3): creates the order, routes it to a manager, confirms
   * receipt to the customer with a reference number, and takes no payment.
   *
   * @param id the session's id
   * @param customer the signed-in customer, if any
   * @param request optionally the language of the confirmation
   * @return the reference; 409 if the order can be priced automatically
   */
  @PostMapping("/{id}/handoff")
  public Mono<HandoffView> handoff(
      @PathVariable String id,
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @RequestBody(required = false) HandoffRequest request) {
    return handoffs
        .handoff(id, customer, request == null ? null : request.locale())
        .map(DeliveryViews::handoff);
  }
}
