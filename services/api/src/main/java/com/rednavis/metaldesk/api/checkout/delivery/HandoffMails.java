package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.account.LocaleParser;
import com.rednavis.metaldesk.api.checkout.CheckoutProperties;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.step1.CustomerDetails;
import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.mail.MailSender;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Tells both parties about a manager handoff, through {@code libs/mail} (BRD FR-5.3).
 *
 * <p>The customer gets a receipt with the reference, in their language. Staff get the <em>full
 * context</em>: the lines, the destination, the value before tax, the weight, and which ceiling
 * bound, so nobody has to go back to the admin UI to reconstruct why the order is there.
 *
 * <p>Sending is best effort: by the time it runs the order exists and the handoff is recorded, so a
 * mail failure is logged, not turned into a failed request that the customer would retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandoffMails {

  private final MailRenderer renderer;
  private final MailSender sender;
  private final CheckoutProperties properties;

  /**
   * Sends the customer's receipt and the staff notification.
   *
   * @param session the handed-off session, with its step 1 and delivery state
   * @param reference the handoff reference
   * @param customerLocale the language of the customer's mail
   * @return a signal that completes when both were handed to the sender, and never fails
   */
  public Mono<Void> announce(CheckoutSession session, String reference, Locale customerLocale) {
    final CustomerDetails details = session.details().orElseThrow();
    final DeliveryState delivery = session.delivery().orElseThrow();
    final TransactionalMail receipt =
        mail(
            MailTemplate.HANDOFF_RECEIPT_CUSTOMER,
            details.email(),
            Map.of("reference", reference, "name", details.name()),
            customerLocale);
    final TransactionalMail notification =
        mail(
            MailTemplate.HANDOFF_NOTIFICATION_STAFF,
            new EmailAddress(properties.staffEmail()),
            Map.of(
                "reference",
                reference,
                "ceiling",
                ceilingLabel(delivery.reason().orElseThrow()),
                "customerName",
                details.name(),
                "customerEmail",
                details.email().value(),
                "destination",
                destination(details.deliveryAddress()),
                "exTaxValue",
                delivery.exTaxValue(),
                "weight",
                delivery.weight().amount().toPlainString() + " g",
                "lines",
                lines(session.lines())),
            LocaleParser.parse(properties.staffLocale()));
    return sender
        .send(receipt)
        .then(sender.send(notification))
        .doOnError(failure -> log.error("Handoff mail failed for reference {}", reference, failure))
        .onErrorResume(failure -> Mono.empty());
  }

  private TransactionalMail mail(
      MailTemplate template, EmailAddress to, Map<String, Object> model, Locale locale) {
    return TransactionalMail.from(
        template, renderer.render(template, model, locale), List.of(to), List.of());
  }

  private static String ceilingLabel(HandoffReason reason) {
    return switch (reason) {
      case VALUE_CEILING_EXCEEDED -> "value";
      case WEIGHT_CEILING_EXCEEDED -> "weight";
      case NO_TIER_FOR_REGION -> "no tier configured for the region";
    };
  }

  private static String destination(Address address) {
    return address.street()
        + ", "
        + address.postalCode()
        + " "
        + address.city()
        + ", "
        + address.country().code();
  }

  private static String lines(List<OrderLine> lines) {
    return lines.stream()
        .map(
            line ->
                "- "
                    + line.quantity().value()
                    + " x "
                    + line.productName()
                    + " ("
                    + line.price().unitPrice().amount().toPlainString()
                    + " "
                    + line.price().unitPrice().currency().code()
                    + " each)")
        .collect(Collectors.joining("\n"));
  }
}
