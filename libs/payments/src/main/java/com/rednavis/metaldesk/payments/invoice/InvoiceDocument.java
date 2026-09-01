package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * A rendered invoice document: the bytes of the file, what to call it, which tax treatment it
 * covers and the locale it was rendered in.
 *
 * <p>The bytes are copied on the way in and on the way out, so a document cannot be altered after
 * it is rendered. Equality is by content, and the string form shows the size, never the bytes.
 *
 * @param number the invoice number the document belongs to, never null
 * @param filename the file name, never blank
 * @param scope the tax treatment the document covers, never null
 * @param locale the locale it was rendered in, never null
 * @param bytes the file's bytes, never empty; the record holds and returns copies
 */
public record InvoiceDocument(
    InvoiceNumber number, String filename, TaxCategory scope, Locale locale, byte[] bytes) {

  /**
   * Validates the fields and copies the bytes.
   *
   * @throws ValidationException if a field is null, the file name is blank or the bytes are empty
   */
  public InvoiceDocument {
    if (number == null || scope == null || locale == null) {
      throw new ValidationException(
          "invoice-document.field-missing", "Invoice document is missing a required field");
    }
    if (filename == null || filename.isBlank()) {
      throw new ValidationException(
          "invoice-document.filename-blank", "Invoice document file name must not be blank");
    }
    if (bytes == null || bytes.length == 0) {
      throw new ValidationException(
          "invoice-document.bytes-empty", "Invoice document must have content");
    }
    bytes = bytes.clone();
  }

  /**
   * Returns a copy of the file's bytes.
   *
   * @return the bytes
   */
  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof InvoiceDocument that
        && number.equals(that.number)
        && filename.equals(that.filename)
        && scope == that.scope
        && locale.equals(that.locale)
        && Arrays.equals(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(number, filename, scope, locale, Arrays.hashCode(bytes));
  }

  @Override
  public String toString() {
    return "InvoiceDocument[" + filename + ", " + bytes.length + " bytes, " + locale + "]";
  }
}
