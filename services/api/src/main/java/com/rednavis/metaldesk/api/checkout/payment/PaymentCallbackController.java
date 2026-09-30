package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentResultView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Where a redirect or element payment returns to (BRD FR-7.2). It is public, because the provider
 * or the browser calls it, and it is therefore <strong>verified, not trusted</strong>: the only
 * parameters read are {@code checkout} and {@code reference}, and the outcome comes from the
 * provider's own confirmation of that reference (see {@link PaymentCallbackService}). Any other
 * parameter, including one claiming success, is ignored.
 */
@RestController
@RequiredArgsConstructor
public class PaymentCallbackController {

  private final PaymentCallbackService callbacks;

  /**
   * Confirms a returned payment.
   *
   * @param checkout the checkout session's id
   * @param reference the provider's reference
   * @return the result, from the provider's confirmation
   */
  @RequestMapping(
      path = "/api/checkout/payment/callback",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Mono<PaymentResultView> callback(
      @RequestParam(name = "checkout", required = false) String checkout,
      @RequestParam(name = "reference", required = false) String reference) {
    return callbacks.confirm(checkout, reference);
  }
}
