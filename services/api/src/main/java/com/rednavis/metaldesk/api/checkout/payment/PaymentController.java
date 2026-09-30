package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.payment.dto.ExecutePaymentRequest;
import com.rednavis.metaldesk.api.checkout.payment.dto.OverviewView;
import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentMethodsView;
import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentResultView;
import com.rednavis.metaldesk.api.checkout.payment.dto.SelectMethodRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Checkout steps 2 to 4: the payment methods, the overview and the payment itself (BRD sections 7.6
 * and 7.7). Every endpoint refuses a session that needs a manager with a 409, before it does
 * anything else.
 */
@RestController
@RequestMapping("/api/checkout/sessions/{id}")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentMethodOfferService offers;
  private final CheckoutOverviewService overviews;
  private final PaymentExecutionService execution;

  /**
   * Lists the payment methods on offer for this checkout.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, if any
   * @return the methods, computed for the order total
   */
  @GetMapping("/payment/methods")
  public Mono<PaymentMethodsView> methods(
      @PathVariable String id, @AuthenticationPrincipal AuthenticatedCustomer customer) {
    return offers.offer(id, customer);
  }

  /**
   * Chooses a payment method.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, if any
   * @param request the method
   * @return the offer, with the method selected
   */
  @PutMapping("/payment/method")
  public Mono<PaymentMethodsView> select(
      @PathVariable String id,
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @RequestBody SelectMethodRequest request) {
    return offers.select(id, customer, request.method());
  }

  /**
   * The final order overview.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, if any
   * @return the overview
   */
  @GetMapping("/overview")
  public Mono<OverviewView> overview(
      @PathVariable String id, @AuthenticationPrincipal AuthenticatedCustomer customer) {
    return overviews.overview(id, customer);
  }

  /**
   * Pays for the order with the selected method.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, if any
   * @param request the total the customer confirmed
   * @return the result: paid, redirect, element, invoice issued, declined or an error; all of them
   *     leave the checkout intact
   */
  @PostMapping("/payment/execute")
  public Mono<PaymentResultView> execute(
      @PathVariable String id,
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @RequestBody(required = false) ExecutePaymentRequest request) {
    return execution.execute(id, customer, request);
  }
}
