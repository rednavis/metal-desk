package com.rednavis.metaldesk.api.payments;

import com.rednavis.metaldesk.payments.invoice.InvoiceDocument;
import com.rednavis.metaldesk.payments.invoice.InvoiceNumber;
import com.rednavis.metaldesk.payments.invoice.InvoiceSink;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * The stand-in receiver of rendered invoices, until confirmation and notification (T-038) mails
 * them to the customer and staff (BRD FR-6.2). It records only that documents were produced, never
 * their content.
 */
@Slf4j
public class LoggingInvoiceSink implements InvoiceSink {

  @Override
  public Mono<Void> publish(InvoiceNumber number, List<InvoiceDocument> documents) {
    return Mono.fromRunnable(
        () -> log.info("Invoice {} rendered as {} document(s)", number, documents.size()));
  }
}
