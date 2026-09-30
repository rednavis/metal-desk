package com.rednavis.metaldesk.api.payments;

import com.rednavis.metaldesk.payments.invoice.InvoiceDocument;
import com.rednavis.metaldesk.payments.invoice.InvoiceNumber;
import com.rednavis.metaldesk.payments.invoice.InvoiceSink;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Receives the invoices the invoice provider renders and archives them for confirmation, which
 * attaches them to the mails to the customer and staff (BRD FR-6.2). It records only that documents
 * were produced in the log, never their content.
 */
@Slf4j
@RequiredArgsConstructor
public class ArchivingInvoiceSink implements InvoiceSink {

  private final InvoiceArchive archive;

  @Override
  public Mono<Void> publish(InvoiceNumber number, List<InvoiceDocument> documents) {
    return archive
        .store(number, documents)
        .doOnSuccess(
            done -> log.info("Invoice {} archived as {} document(s)", number, documents.size()));
  }
}
