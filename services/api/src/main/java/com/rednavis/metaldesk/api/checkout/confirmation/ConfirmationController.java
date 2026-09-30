package com.rednavis.metaldesk.api.checkout.confirmation;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.confirmation.dto.ConfirmationView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Checkout step 5: the confirmation (BRD FR-8.1). */
@RestController
@RequestMapping("/api/checkout/sessions/{id}")
@RequiredArgsConstructor
public class ConfirmationController {

  private final ConfirmationService confirmation;

  /**
   * Confirms a checkout, sending whatever has not been sent, and returns the confirmation.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, or null for a guest
   * @return the confirmation
   */
  @GetMapping("/confirmation")
  public Mono<ConfirmationView> show(
      @PathVariable String id, @AuthenticationPrincipal AuthenticatedCustomer customer) {
    return confirmation.confirm(id, customer);
  }
}
