package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Locale;

/**
 * Everything an {@link InvoiceRenderer} needs to draw one invoice document, so a renderer is a pure
 * function of this and reads nothing else, in particular no default locale and no clock.
 *
 * <p>The totals are those of this document's own lines. When an order is split in two, the delivery
 * cost appears on the first document only, so that the documents' grand totals add up to the
 * order's grand total exactly (BRD BR-5).
 *
 * @param number the invoice number all documents of the order share, never null
 * @param orderNumber the order's number, never null
 * @param scope the lines this document covers, never null
 * @param totals the totals of this document, never null
 * @param locale the language and number format to render in, never null
 * @param position which document this is, from 1
 * @param count how many documents the invoice has, from {@code position} up to {@value
 *     InvoiceSplitter#MAX_DOCUMENTS}
 */
public record InvoiceContent(
    InvoiceNumber number,
    OrderNumber orderNumber,
    DocumentScope scope,
    OrderTotals totals,
    Locale locale,
    int position,
    int count) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if a field is null, or the position and count are not {@code 1 <=
   *     position <= count <= 2}
   */
  public InvoiceContent {
    if (number == null
        || orderNumber == null
        || scope == null
        || totals == null
        || locale == null) {
      throw new ValidationException(
          "invoice-content.field-missing", "Invoice content is missing a required field");
    }
    if (position < 1 || position > count || count > InvoiceSplitter.MAX_DOCUMENTS) {
      throw new ValidationException(
          "invoice-content.position-invalid",
          "Document " + position + " of " + count + " is not a valid position");
    }
  }
}
