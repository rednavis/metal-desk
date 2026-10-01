package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.payment.dto.ResumedPaymentView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Paying an order that is awaiting payment, from the order itself. Needs a token. */
@RestController
@RequestMapping("/api/orders/{number}")
@RequiredArgsConstructor
public class PaymentResumeController {

  private final PaymentResumeService resume;

  /**
   * Starts a payment session for the order.
   *
   * @param customer the signed-in customer
   * @param number the order number
   * @return the checkout to continue in; 404 if the order is not the caller's, 409 if it is not
   *     awaiting payment
   */
  @PostMapping("/payment-session")
  public Mono<ResumedPaymentView> start(
      @AuthenticationPrincipal AuthenticatedCustomer customer, @PathVariable String number) {
    return resume.resume(customer, number).map(session -> new ResumedPaymentView(session.id()));
  }
}
