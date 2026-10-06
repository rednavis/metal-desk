package com.rednavis.metaldesk.admin.quote;

import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.mail.MailSender;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.fulfillment.ManagerQuote;
import com.rednavis.metaldesk.share.domain.order.Order;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Tells the customer their terms are set, with the template in {@code libs/mail}.
 *
 * <p>The mail goes out without waiting for it: the state change has already been made, and a mail
 * server that is down must not turn a completed staff action into an error, or make staff repeat
 * it. A failure is logged with the order number. The customer's language is not stored anywhere, so
 * the mail is in English, as {@code services/api}'s account mails are when none is given.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteMails {

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

  private final MailRenderer renderer;
  private final MailSender sender;

  /**
   * Sends the terms to the customer.
   *
   * @param order the order, already awaiting payment, with the manager's delivery quote
   * @param customer the customer record
   * @param quote what staff decided
   * @param validUntil until when the customer may pay on it
   */
  public void announce(
      Order order, CustomerDocument customer, ManagerQuote quote, Instant validUntil) {
    final DeliveryQuote delivery = order.quote().orElseThrow();
    final String number = order.number().format();
    final TransactionalMail mail =
        TransactionalMail.from(
            MailTemplate.MANAGER_QUOTE_CUSTOMER,
            renderer.render(
                MailTemplate.MANAGER_QUOTE_CUSTOMER,
                Map.of(
                    "reference", number,
                    "name", customer.name(),
                    "deliveryPrice", quote.finalPrice(),
                    "total", order.totals().grandTotal(),
                    "transit",
                        delivery.transit().minDays() + "-" + delivery.transit().maxDays() + " days",
                    "validUntil", DAY.format(validUntil),
                    "terms", quote.terms()),
                Locale.ENGLISH),
            List.of(new EmailAddress(customer.email())),
            List.of());
    sender
        .send(mail)
        .doOnError(failure -> log.error("Quote mail failed for order {}", number, failure))
        .onErrorResume(failure -> Mono.empty())
        .subscribe();
  }
}
