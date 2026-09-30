package com.rednavis.metaldesk.api.checkout.confirmation;

import com.rednavis.metaldesk.api.payments.InvoiceArchive;
import com.rednavis.metaldesk.api.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.mail.MailAttachment;
import com.rednavis.metaldesk.mail.MailSender;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.payments.invoice.InvoiceDocument;
import com.rednavis.metaldesk.payments.invoice.InvoiceNumber;
import com.rednavis.metaldesk.share.domain.order.Order;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Sends the mails that close an order, one per recipient and template, each at most once per order
 * (BRD FR-8.1, FR-6.2).
 *
 * <p>The invoice mails carry the documents that were rendered during payment and archived then;
 * nothing is rendered here, because a second rendering could differ from the one the payment record
 * refers to.
 */
@Component
@RequiredArgsConstructor
public class OrderNotificationService {

  private static final String PDF = "application/pdf";

  private final NotificationLedger ledger;
  private final CustomerRepository customers;
  private final InvoiceArchive archive;
  private final OrderMails mails;
  private final MailSender sender;

  /**
   * Sends the order confirmation to the customer and the order notification to staff.
   *
   * @param order the order that was placed
   * @param locale the customer's language
   * @return a signal that completes when both were sent or were already sent
   */
  public Mono<Void> orderPlaced(Order order, Locale locale) {
    return customer(order)
        .flatMap(
            customer ->
                once(
                        order,
                        MailTemplate.ORDER_CONFIRMATION_CUSTOMER,
                        () -> mails.confirmation(order, customer, locale))
                    .then(
                        once(
                            order,
                            MailTemplate.ORDER_NOTIFICATION_STAFF,
                            () -> mails.notification(order, customer))));
  }

  /**
   * Sends the invoice documents to the customer and to staff. Where the order had two tax
   * treatments there are two documents, and both go to both recipients.
   *
   * @param order the order an invoice was issued for
   * @param locale the customer's language
   * @return a signal that completes when both were sent or were already sent
   * @throws IllegalStateException if no documents were archived for the order
   */
  public Mono<Void> invoiceIssued(Order order, Locale locale) {
    final InvoiceNumber number = InvoiceNumber.forOrder(order.number());
    return Mono.zip(
            customer(order),
            archive
                .find(number)
                .filter(documents -> !documents.isEmpty())
                .switchIfEmpty(
                    Mono.error(
                        () ->
                            new IllegalStateException(
                                "No invoice documents were archived for " + number))))
        .flatMap(pair -> invoiceMails(order, pair.getT1(), pair.getT2(), locale));
  }

  private Mono<Void> invoiceMails(
      Order order, CustomerDocument customer, List<InvoiceDocument> documents, Locale locale) {
    final List<MailAttachment> attachments =
        documents.stream()
            .map(document -> new MailAttachment(document.filename(), PDF, document.bytes()))
            .toList();
    return once(
            order,
            MailTemplate.INVOICE_CUSTOMER,
            () -> mails.invoiceToCustomer(order, customer, locale, attachments))
        .then(
            once(
                order,
                MailTemplate.INVOICE_STAFF,
                () -> mails.invoiceToStaff(order, customer, attachments)));
  }

  private Mono<CustomerDocument> customer(Order order) {
    return customers
        .findById(order.customerId().value())
        .switchIfEmpty(
            Mono.error(
                () ->
                    new IllegalStateException("No customer for order " + order.number().format())));
  }

  /** Sends a mail unless the order already had it; a failed send gives its claim back. */
  private Mono<Void> once(Order order, MailTemplate template, Supplier<TransactionalMail> mail) {
    return ledger
        .claim(order, template)
        .filter(claimed -> claimed)
        .flatMap(
            claimed ->
                Mono.fromSupplier(mail)
                    .flatMap(sender::send)
                    .onErrorResume(
                        failure -> ledger.release(order, template).then(Mono.error(failure))));
  }
}
