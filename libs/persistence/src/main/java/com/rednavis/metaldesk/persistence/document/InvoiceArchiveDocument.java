package com.rednavis.metaldesk.persistence.document;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The documents rendered for one invoice number, kept so confirmation attaches exactly what was
 * rendered during payment instead of rendering again.
 *
 * @param id the invoice number
 * @param files the documents, one per tax scope
 */
@Document("invoice_archive")
public record InvoiceArchiveDocument(@Id String id, List<InvoiceFileDocument> files) {

  /** Copies the list, so the record cannot be changed through it. */
  public InvoiceArchiveDocument {
    files = files == null ? List.of() : List.copyOf(files);
  }
}
