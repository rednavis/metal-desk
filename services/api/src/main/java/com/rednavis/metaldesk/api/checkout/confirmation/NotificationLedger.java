package com.rednavis.metaldesk.api.checkout.confirmation;

import com.rednavis.metaldesk.api.persistence.repository.NotificationRepository;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.persistence.document.NotificationDocument;
import com.rednavis.metaldesk.share.domain.order.Order;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Remembers which notification was sent for which order, so a retried confirmation, a duplicate
 * provider callback or a reconciliation job cannot mail the customer twice.
 *
 * <p>A notification is <em>claimed</em> before it is sent: the claim is an insert whose id is the
 * order number and the template, so of two concurrent confirmations exactly one wins. A send that
 * then fails releases its claim, so the next confirmation tries again.
 */
@Component
@RequiredArgsConstructor
public class NotificationLedger {

  private final NotificationRepository repository;
  private final Clock clock;

  /**
   * Claims the right to send a notification.
   *
   * @param order the order
   * @param template the notification
   * @return true if this caller must send it, false if it was already claimed
   */
  public Mono<Boolean> claim(Order order, MailTemplate template) {
    return repository
        .insert(
            new NotificationDocument(
                key(order, template), order.id().value(), template.name(), clock.instant()))
        .thenReturn(true)
        .onErrorResume(DuplicateKeyException.class, duplicate -> Mono.just(false));
  }

  /**
   * Gives a claim back after the send failed.
   *
   * @param order the order
   * @param template the notification
   * @return a signal that completes when the claim is released
   */
  public Mono<Void> release(Order order, MailTemplate template) {
    return repository.deleteById(key(order, template));
  }

  private static String key(Order order, MailTemplate template) {
    return order.number().format() + ":" + template.name();
  }
}
