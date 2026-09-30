package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.payments.invoice.InvoiceDocument;
import com.rednavis.metaldesk.payments.invoice.InvoiceNumber;
import com.rednavis.metaldesk.payments.invoice.InvoiceSink;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Records the invoices the invoice provider renders, so a test can see them. */
@Primary
@Component
public class RecordingInvoiceSink implements InvoiceSink {

  private final List<InvoiceNumber> recordedNumbers = new CopyOnWriteArrayList<>();
  private final List<InvoiceDocument> recordedDocuments = new CopyOnWriteArrayList<>();

  @Override
  public Mono<Void> publish(InvoiceNumber number, List<InvoiceDocument> published) {
    return Mono.fromRunnable(
        () -> {
          recordedNumbers.add(number);
          recordedDocuments.addAll(published);
        });
  }

  /**
   * The invoice numbers published so far.
   *
   * @return the numbers
   */
  public List<InvoiceNumber> numbers() {
    return List.copyOf(recordedNumbers);
  }

  /**
   * The documents published so far.
   *
   * @return the documents
   */
  public List<InvoiceDocument> documents() {
    return List.copyOf(recordedDocuments);
  }
}
