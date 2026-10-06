package com.rednavis.metaldesk.api.payments;

import com.rednavis.metaldesk.api.persistence.repository.InvoiceArchiveRepository;
import com.rednavis.metaldesk.payments.invoice.InvoiceDocument;
import com.rednavis.metaldesk.payments.invoice.InvoiceNumber;
import com.rednavis.metaldesk.persistence.document.InvoiceArchiveDocument;
import com.rednavis.metaldesk.persistence.document.InvoiceFileDocument;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Keeps the invoice documents rendered during payment until confirmation mails them (BRD FR-6.2).
 *
 * <p>Confirmation reads from here and never renders: a second rendering could produce a document
 * that differs from the one the payment record refers to.
 */
@Component
@RequiredArgsConstructor
public class InvoiceArchive {

  private final InvoiceArchiveRepository repository;

  /**
   * Stores the documents of an invoice, replacing any stored under the same number.
   *
   * @param number the invoice number
   * @param documents the rendered documents
   * @return a signal that completes when they are stored
   */
  public Mono<Void> store(InvoiceNumber number, List<InvoiceDocument> documents) {
    final List<InvoiceFileDocument> files =
        documents.stream()
            .map(
                document ->
                    new InvoiceFileDocument(
                        document.filename(),
                        document.scope().name(),
                        document.locale().toLanguageTag(),
                        Base64.getEncoder().encodeToString(document.bytes())))
            .toList();
    return repository.save(new InvoiceArchiveDocument(number.value(), files)).then();
  }

  /**
   * Reads the documents of an invoice.
   *
   * @param number the invoice number
   * @return the documents, or none if nothing was stored under that number
   */
  public Mono<List<InvoiceDocument>> find(InvoiceNumber number) {
    return repository
        .findById(number.value())
        .map(
            stored ->
                stored.files().stream()
                    .map(
                        file ->
                            new InvoiceDocument(
                                number,
                                file.filename(),
                                TaxCategory.valueOf(file.scope()),
                                Locale.forLanguageTag(file.locale()),
                                Base64.getDecoder().decode(file.contentBase64())))
                    .toList())
        .defaultIfEmpty(List.of());
  }
}
