package com.rednavis.metaldesk.api.checkout.confirmation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rednavis.metaldesk.mail.MailAttachment;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.payments.invoice.InvoiceDocument;
import com.rednavis.metaldesk.payments.invoice.InvoiceRenderer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The invoice mails (BRD FR-6.2): they carry the documents rendered during payment, both to the
 * customer and to staff, and confirming never renders again.
 */
class ConfirmationInvoiceTest extends ConfirmationTestSupport {

  private static final String INVOICE_KIND = "INVOICE";

  @MockitoSpyBean private InvoiceRenderer spiedRenderer;

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  @Test
  void invoiceOrderMailsTheRenderedDocumentToTheCustomerAndToStaff() {
    final String id = invoicedCheckout(false);
    final List<InvoiceDocument> rendered = invoices.documents();
    clearInvocations(spiedRenderer);

    final Called confirmed = confirm(id);

    assertEquals(200, confirmed.status());
    assertEquals(INVOICE_KIND, confirmed.body().get("kind"));
    final String number = (String) confirmed.body().get("orderNumber");
    assertEquals("INV-" + number, confirmed.body().get("invoiceNumber"));
    final TransactionalMail toCustomer = single(MailTemplate.INVOICE_CUSTOMER, number);
    final TransactionalMail toStaff = single(MailTemplate.INVOICE_STAFF, number);
    assertEquals(1, toCustomer.attachments().size());
    assertEquals(1, toStaff.attachments().size());
    assertArrayEquals(
        rendered.get(rendered.size() - 1).bytes(), toCustomer.attachments().get(0).bytes());
    assertEquals(toCustomer.attachments(), toStaff.attachments());
  }

  @Test
  void mixedTaxOrderAttachesBothDocumentsToBothMailsWithoutRenderingAgain() {
    final String id = invoicedCheckout(true);
    clearInvocations(spiedRenderer);

    final String number = numberOf(id);

    final TransactionalMail toCustomer = single(MailTemplate.INVOICE_CUSTOMER, number);
    final TransactionalMail toStaff = single(MailTemplate.INVOICE_STAFF, number);
    assertEquals(2, toCustomer.attachments().size());
    assertEquals(2, toStaff.attachments().size());
    assertEquals(
        2, toCustomer.attachments().stream().map(MailAttachment::filename).distinct().count());
    verifyNoInteractions(spiedRenderer);
  }

  @Test
  void invoiceOrderAlsoGetsTheOrderConfirmationOnce() {
    final String id = invoicedCheckout(false);
    final String number = numberOf(id);

    confirm(id);

    assertEquals(1, mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, number).size());
    assertEquals(1, mailsOf(MailTemplate.INVOICE_CUSTOMER, number).size());
    assertEquals(1, mailsOf(MailTemplate.INVOICE_STAFF, number).size());
  }

  private TransactionalMail single(MailTemplate template, String number) {
    final List<TransactionalMail> sent = mailsOf(template, number);
    assertEquals(1, sent.size(), template + " for " + number);
    return sent.get(0);
  }
}
