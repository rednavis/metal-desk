package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The invoice rule of BRD FR-6.2: an order is invoiced in one document, or in two when its cart
 * mixes tax-exempt and taxable categories, and never in more. A pure function of the lines it is
 * given.
 *
 * <p>Lines are grouped by the {@link TaxCategory} <em>snapshotted on each line</em> when the order
 * was placed, which is why the order line holds it (BRD BR-2). Nothing is resolved from the
 * catalog, so reclassifying a category later cannot move a settled order's lines between documents.
 *
 * <ul>
 *   <li>Lines that all share one tax category give one scope.
 *   <li>Lines that span both give two scopes, the zero-rated one first.
 *   <li>No lines give no scopes.
 * </ul>
 *
 * <p><strong>The cap of two is tied to {@link TaxCategory} having exactly two members</strong> (BRD
 * BR-4: investment-grade is zero-rated, everything else standard-rated). If a third tax category is
 * ever added, this rule and FR-6.2 both need revisiting. That is why {@link #MAX_DOCUMENTS} is the
 * literal 2 rather than the number of categories: a new category must fail loudly here, not widen
 * the cap by itself.
 */
public final class InvoiceSplitter {

  /** The most documents one order may be invoiced in (BRD FR-6.2). */
  public static final int MAX_DOCUMENTS = 2;

  private InvoiceSplitter() {}

  /**
   * Splits an order's lines into invoice document scopes.
   *
   * @param lines the order's lines, possibly empty
   * @return one scope per tax category present, in the order of {@link TaxCategory}; at most
   *     {@value #MAX_DOCUMENTS}
   * @throws ValidationException if the list is null or holds a null
   * @throws IllegalStateException if more than {@value #MAX_DOCUMENTS} scopes would result, which
   *     can only happen if {@link TaxCategory} has gained a member and this rule was not revisited
   */
  public static List<DocumentScope> split(List<OrderLine> lines) {
    if (lines == null || lines.stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(
          "invoice-splitter.lines-invalid", "Lines must be present and hold no null line");
    }
    final List<DocumentScope> scopes =
        lines.stream()
            .collect(
                Collectors.groupingBy(
                    OrderLine::taxCategory,
                    () -> new EnumMap<>(TaxCategory.class),
                    Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> new DocumentScope(entry.getKey(), entry.getValue()))
            .toList();
    if (scopes.size() > MAX_DOCUMENTS) {
      throw new IllegalStateException(
          "FR-6.2 caps an invoice at "
              + MAX_DOCUMENTS
              + " documents but "
              + scopes.size()
              + " tax categories are present; revisit this rule");
    }
    return scopes;
  }
}
