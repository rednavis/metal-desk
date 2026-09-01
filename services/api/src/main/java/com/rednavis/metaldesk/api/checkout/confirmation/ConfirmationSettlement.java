package com.rednavis.metaldesk.api.checkout.confirmation;

import com.rednavis.metaldesk.api.checkout.payment.OrderSettlement;
import com.rednavis.metaldesk.share.domain.order.Order;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The hand-off from payment to confirmation: when an order is paid, or an invoice issued for it,
 * the mails go out.
 *
 * <p>A failure here is logged, never passed on. The payment was already taken, so telling the
 * customer "payment failed" because a mail could not be sent would be false, and a retry would
 * charge again. The confirmation endpoint sends what is missing when the customer or the client
 * asks again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmationSettlement implements OrderSettlement {

  private final ConfirmationService confirmation;

  @Override
  public Mono<Void> paid(Order order, Locale locale) {
    return settle(order, locale);
  }

  @Override
  public Mono<Void> invoiceIssued(Order order, Locale locale) {
    return settle(order, locale);
  }

  private Mono<Void> settle(Order order, Locale locale) {
    return confirmation
        .settle(order, locale)
        .onErrorResume(
            failure -> {
              if (log.isErrorEnabled()) {
                log.error(
                    "Confirmation of order {} is pending a retry",
                    order.number().format(),
                    failure);
              }
              return Mono.empty();
            });
  }
}
