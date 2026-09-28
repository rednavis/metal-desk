package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Objects;

/**
 * What one invoice document covers: the order lines of a single tax treatment (BRD FR-6.2, BR-4).
 *
 * <p>A scope is never mixed. Every line in it carries the tax category that was snapshotted on the
 * line when the order was placed, and that is what the scope is named by; nothing is looked up from
 * the catalog, so a later reclassification cannot change which document a line belongs on (BRD
 * BR-2).
 *
 * @param taxCategory the tax treatment every line in this scope carries, never null
 * @param lines the lines, at least one, all carrying {@code taxCategory}; copied, so unmodifiable
 */
public record DocumentScope(TaxCategory taxCategory, List<OrderLine> lines) {

  /**
   * Validates the fields and copies the list.
   *
   * @throws ValidationException if the category is null, the lines are null, empty or hold a null,
   *     or a line carries a different tax category
   */
  public DocumentScope {
    if (taxCategory == null
        || lines == null
        || lines.isEmpty()
        || lines.stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(
          "document-scope.invalid", "A document scope needs a tax category and at least one line");
    }
    if (lines.stream().anyMatch(line -> line.taxCategory() != taxCategory)) {
      throw new ValidationException(
          "document-scope.mixed", "Every line in a document scope must share its tax category");
    }
    lines = List.copyOf(lines);
  }
}
