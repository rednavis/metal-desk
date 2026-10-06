package com.rednavis.metaldesk.payments.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.order.OrderTotalsCalculator;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** The refusals of the invoice value types that the main tests leave out. */
class InvoiceValidationGapsTest {

  private static final OrderNumber ORDER_NUMBER = new OrderNumber(LocalDate.of(2022, 2, 8), 4);
  private static final InvoiceNumber NUMBER = InvoiceNumber.forOrder(ORDER_NUMBER);
  private static final String FILE = "a.pdf";
  private static final String CONTENT_MISSING = "invoice-content.field-missing";
  private static final byte[] PDF = "%PDF-test".getBytes(StandardCharsets.US_ASCII);

  private static String codeOf(Executable action) {
    return assertThrows(ValidationException.class, action).code();
  }

  private static InvoiceDocument document(
      InvoiceNumber number, String name, TaxCategory scope, Locale locale, byte[] bytes) {
    return new InvoiceDocument(number, name, scope, locale, bytes);
  }

  private static InvoiceDocument base() {
    return document(NUMBER, FILE, TaxCategory.STANDARD, Locale.ENGLISH, PDF.clone());
  }

  @Test
  void documentWithoutNumberScopeOrLocaleIsRefused() {
    assertEquals(
        "invoice-document.field-missing",
        codeOf(() -> document(null, FILE, TaxCategory.STANDARD, Locale.ENGLISH, PDF.clone())));
    assertEquals(
        "invoice-document.field-missing",
        codeOf(() -> document(NUMBER, FILE, null, Locale.ENGLISH, PDF.clone())));
    assertEquals(
        "invoice-document.field-missing",
        codeOf(() -> document(NUMBER, FILE, TaxCategory.STANDARD, null, PDF.clone())));
  }

  @Test
  void documentWithoutFilenameOrBytesIsRefused() {
    assertEquals(
        "invoice-document.filename-blank",
        codeOf(() -> document(NUMBER, null, TaxCategory.STANDARD, Locale.ENGLISH, PDF.clone())));
    assertEquals(
        "invoice-document.bytes-empty",
        codeOf(() -> document(NUMBER, FILE, TaxCategory.STANDARD, Locale.ENGLISH, null)));
  }

  @Test
  void documentsDifferByAnyField() {
    final InvoiceDocument base = base();
    final InvoiceNumber other =
        InvoiceNumber.forOrder(new OrderNumber(LocalDate.of(2022, 2, 8), 5));
    assertNotEquals(base, document(other, FILE, TaxCategory.STANDARD, Locale.ENGLISH, PDF.clone()));
    assertNotEquals(
        base, document(NUMBER, "b.pdf", TaxCategory.STANDARD, Locale.ENGLISH, PDF.clone()));
    assertNotEquals(
        base, document(NUMBER, FILE, TaxCategory.INVESTMENT_GRADE, Locale.ENGLISH, PDF.clone()));
    assertNotEquals(base, document(NUMBER, FILE, TaxCategory.STANDARD, Locale.GERMAN, PDF.clone()));
    assertNotEquals(base, FILE);
    assertEquals(base.hashCode(), base().hashCode());
  }

  @Test
  void invoiceNumberRefusesNullAndMissingOrderNumbers() {
    assertEquals("invoice-number.malformed", codeOf(() -> new InvoiceNumber(null)));
    assertEquals("invoice-number.order-missing", codeOf(() -> InvoiceNumber.forOrder(null)));
  }

  @Test
  void scopeRefusesMissingPartsAndMixedCategories() {
    final List<OrderLine> withNull = new ArrayList<>();
    withNull.add(null);
    assertEquals(
        "document-scope.invalid",
        codeOf(() -> new DocumentScope(null, List.of(InvoiceFixtures.goldLine()))));
    assertEquals(
        "document-scope.invalid", codeOf(() -> new DocumentScope(TaxCategory.STANDARD, null)));
    assertEquals(
        "document-scope.invalid", codeOf(() -> new DocumentScope(TaxCategory.STANDARD, withNull)));
    assertEquals(
        "document-scope.mixed",
        codeOf(() -> new DocumentScope(TaxCategory.STANDARD, List.of(InvoiceFixtures.goldLine()))));
  }

  @Test
  void contentRefusesMissingFields() {
    final DocumentScope scope =
        new DocumentScope(TaxCategory.INVESTMENT_GRADE, List.of(InvoiceFixtures.goldLine()));
    final OrderTotals totals =
        OrderTotalsCalculator.compute(scope.lines(), InvoiceFixtures.eur("0.00"));
    assertEquals(
        CONTENT_MISSING,
        codeOf(() -> new InvoiceContent(null, ORDER_NUMBER, scope, totals, Locale.ENGLISH, 1, 1)));
    assertEquals(
        CONTENT_MISSING,
        codeOf(() -> new InvoiceContent(NUMBER, null, scope, totals, Locale.ENGLISH, 1, 1)));
    assertEquals(
        CONTENT_MISSING,
        codeOf(() -> new InvoiceContent(NUMBER, ORDER_NUMBER, null, totals, Locale.ENGLISH, 1, 1)));
    assertEquals(
        CONTENT_MISSING,
        codeOf(() -> new InvoiceContent(NUMBER, ORDER_NUMBER, scope, null, Locale.ENGLISH, 1, 1)));
    assertEquals(
        CONTENT_MISSING,
        codeOf(() -> new InvoiceContent(NUMBER, ORDER_NUMBER, scope, totals, null, 1, 1)));
  }
}
