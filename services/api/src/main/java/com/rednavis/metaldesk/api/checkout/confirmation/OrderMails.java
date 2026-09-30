package com.rednavis.metaldesk.api.checkout.confirmation;

import com.rednavis.metaldesk.api.account.LocaleParser;
import com.rednavis.metaldesk.api.checkout.CheckoutProperties;
import com.rednavis.metaldesk.api.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.mail.MailAttachment;
import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.payments.invoice.InvoiceNumber;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.order.Order;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds the four mails that close an order: the customer's in the customer's language, staff's in
 * the staff language. It only builds; whether a mail is sent is decided by {@link
 * OrderNotificationService}.
 */
@Component
@RequiredArgsConstructor
public class OrderMails {

  private static final String ORDER_NUMBER = "orderNumber";
  private static final String INVOICE_NUMBER = "invoiceNumber";
  private static final String CUSTOMER_NAME = "customerName";
  private static final String TOTAL = "total";

  private final MailRenderer renderer;
  private final CheckoutProperties properties;

  /**
   * The order confirmation to the customer.
   *
   * @param order the order
   * @param customer the customer
   * @param locale the customer's language
   * @return the mail
   */
  public TransactionalMail confirmation(Order order, CustomerDocument customer, Locale locale) {
    return build(
        MailTemplate.ORDER_CONFIRMATION_CUSTOMER,
        new EmailAddress(customer.email()),
        Map.of(
            "name",
            customer.name(),
            ORDER_NUMBER,
            order.number().format(),
            TOTAL,
            order.totals().grandTotal()),
        locale,
        List.of());
  }

  /**
   * The order notification to staff.
   *
   * @param order the order
   * @param customer the customer
   * @return the mail
   */
  public TransactionalMail notification(Order order, CustomerDocument customer) {
    return build(
        MailTemplate.ORDER_NOTIFICATION_STAFF,
        staff(),
        Map.of(
            ORDER_NUMBER,
            order.number().format(),
            CUSTOMER_NAME,
            customer.name(),
            TOTAL,
            order.totals().grandTotal()),
        staffLocale(),
        List.of());
  }

  /**
   * The invoice to the customer.
   *
   * @param order the order
   * @param customer the customer
   * @param locale the customer's language
   * @param documents the documents to attach
   * @return the mail
   */
  public TransactionalMail invoiceToCustomer(
      Order order, CustomerDocument customer, Locale locale, List<MailAttachment> documents) {
    return build(
        MailTemplate.INVOICE_CUSTOMER,
        new EmailAddress(customer.email()),
        Map.of(
            "name",
            customer.name(),
            INVOICE_NUMBER,
            InvoiceNumber.forOrder(order.number()).value(),
            ORDER_NUMBER,
            order.number().format()),
        locale,
        documents);
  }

  /**
   * The invoice to staff.
   *
   * @param order the order
   * @param customer the customer
   * @param documents the documents to attach
   * @return the mail
   */
  public TransactionalMail invoiceToStaff(
      Order order, CustomerDocument customer, List<MailAttachment> documents) {
    return build(
        MailTemplate.INVOICE_STAFF,
        staff(),
        Map.of(
            CUSTOMER_NAME,
            customer.name(),
            INVOICE_NUMBER,
            InvoiceNumber.forOrder(order.number()).value(),
            ORDER_NUMBER,
            order.number().format()),
        staffLocale(),
        documents);
  }

  private TransactionalMail build(
      MailTemplate template,
      EmailAddress to,
      Map<String, Object> model,
      Locale locale,
      List<MailAttachment> attachments) {
    return TransactionalMail.from(
        template, renderer.render(template, model, locale), List.of(to), attachments);
  }

  private EmailAddress staff() {
    return new EmailAddress(properties.staffEmail());
  }

  private Locale staffLocale() {
    return LocaleParser.parse(properties.staffLocale());
  }
}
