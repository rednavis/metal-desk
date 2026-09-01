package com.rednavis.metaldesk.payments.invoice;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * The port that receives the invoice documents {@link InvoiceProvider} has rendered.
 *
 * <p>A payment outcome carries only the invoice number, so the documents themselves have to go
 * somewhere: to the customer and a copy to staff by email (BRD FR-6.2, sent through {@code
 * libs/mail}, wired in T-038), and to storage (T-030). Those are not this provider's business, so
 * it hands the documents to this port and an implementation decides. If the sink fails, the issue
 * fails and no invoice is reported as issued.
 */
@FunctionalInterface
public interface InvoiceSink {

  /**
   * Receives the documents of one invoice.
   *
   * @param number the invoice number the documents share
   * @param documents the one or two rendered documents
   * @return a {@code Mono} that completes once they have been accepted
   */
  Mono<Void> publish(InvoiceNumber number, List<InvoiceDocument> documents);
}
