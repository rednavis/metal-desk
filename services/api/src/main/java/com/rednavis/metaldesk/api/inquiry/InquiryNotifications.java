package com.rednavis.metaldesk.api.inquiry;

import com.rednavis.metaldesk.api.account.LocaleParser;
import com.rednavis.metaldesk.api.checkout.CheckoutProperties;
import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.mail.MailSender;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Tells both parties about an inquiry: the customer gets a receipt with the reference, in their
 * language, and staff get the message, in theirs (BRD FR-9.1, FR-9.2).
 *
 * <p>Sending never fails the caller: the inquiry is already stored, so a mail failure is logged at
 * error level with the reference, and a resend by the customer would only make staff read it twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InquiryNotifications {

  private final MailRenderer renderer;
  private final MailSender sender;
  private final CheckoutProperties properties;

  /**
   * Sends the receipt and the staff mail.
   *
   * @param inquiry the stored inquiry
   * @return a signal that completes when both were attempted, and never fails
   */
  public Mono<Void> send(Inquiry inquiry) {
    final TransactionalMail receipt =
        mail(
            MailTemplate.INQUIRY_RECEIPT_CUSTOMER,
            inquiry.email(),
            Map.of(
                "name", inquiry.name(), "topic", inquiry.topic(), "reference", inquiry.reference()),
            inquiry.locale());
    final TransactionalMail notification =
        mail(
            MailTemplate.INQUIRY_NOTIFICATION_STAFF,
            new EmailAddress(properties.staffEmail()),
            Map.of(
                "customerName",
                inquiry.name(),
                "customerEmail",
                inquiry.email().value(),
                "topic",
                inquiry.topic(),
                "message",
                inquiry.message(),
                "reference",
                inquiry.reference()),
            LocaleParser.parse(properties.staffLocale()));
    return Mono.whenDelayError(sender.send(receipt), sender.send(notification))
        .onErrorResume(
            failure -> {
              if (log.isErrorEnabled()) {
                log.error("Inquiry {} is stored but its mail failed", inquiry.reference(), failure);
              }
              return Mono.empty();
            });
  }

  private TransactionalMail mail(
      MailTemplate template, EmailAddress to, Map<String, Object> model, Locale locale) {
    return TransactionalMail.from(
        template, renderer.render(template, model, locale), List.of(to), List.of());
  }
}
