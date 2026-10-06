package com.rednavis.metaldesk.payments.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.order.OrderTotalsCalculator;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class InvoiceValueTypesTest {

  private static final OrderNumber ORDER_NUMBER = new OrderNumber(LocalDate.of(2022, 2, 8), 4);
  private static final InvoiceNumber NUMBER = InvoiceNumber.forOrder(ORDER_NUMBER);
  private static final byte[] PDF = "%PDF-test".getBytes(StandardCharsets.US_ASCII);

  private static InvoiceDocument document(byte[] bytes) {
    return new InvoiceDocument(NUMBER, "a.pdf", TaxCategory.STANDARD, Locale.ENGLISH, bytes);
  }

  @Test
  void invoiceNumberIsDerivedFromTheOrderNumberAndBecomesTheReference() {
    assertEquals("INV-080220220004", NUMBER.value());
    assertEquals("INV-080220220004", NUMBER.toReference().value());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "INV-1", "inv-080220220004", "INV-08022022000x", "080220220004"})
  void malformedInvoiceNumbersAreRefused(String value) {
    assertEquals(
        "invoice-number.malformed",
        assertThrows(ValidationException.class, () -> new InvoiceNumber(value)).code());
  }

  @Test
  void documentBytesAreCopiedInAndOut() {
    final byte[] original = PDF.clone();
    final InvoiceDocument document = document(original);
    original[0] = 0;
    final byte[] returned = document.bytes();
    returned[1] = 0;
    assertEquals('%', document.bytes()[0]);
    assertEquals('P', document.bytes()[1]);
  }

  @Test
  void documentsAreEqualByContentAndTheStringFormHidesTheBytes() {
    assertEquals(document(PDF), document(PDF.clone()));
    assertNotEquals(document(PDF), document("%PDF-other".getBytes(StandardCharsets.US_ASCII)));
    assertEquals("InvoiceDocument[a.pdf, 9 bytes, en]", document(PDF).toString());
  }

  @Test
  void invalidDocumentsAreRefused() {
    assertEquals(
        "invoice-document.bytes-empty",
        assertThrows(ValidationException.class, () -> document(new byte[0])).code());
    assertEquals(
        "invoice-document.filename-blank",
        assertThrows(
                ValidationException.class,
                () ->
                    new InvoiceDocument(
                        NUMBER, " ", TaxCategory.STANDARD, Locale.ENGLISH, PDF.clone()))
            .code());
  }

  @ParameterizedTest
  @CsvSource({"0,1", "2,1", "1,3"})
  void contentPositionMustBeWithinTheCountAndTheCap(int position, int count) {
    final DocumentScope scope =
        new DocumentScope(TaxCategory.INVESTMENT_GRADE, List.of(InvoiceFixtures.goldLine()));
    final OrderTotals totals =
        OrderTotalsCalculator.compute(scope.lines(), InvoiceFixtures.eur("0.00"));
    assertEquals(
        "invoice-content.position-invalid",
        assertThrows(
                ValidationException.class,
                () ->
                    new InvoiceContent(
                        NUMBER, ORDER_NUMBER, scope, totals, Locale.ENGLISH, position, count))
            .code());
  }

  @Test
  void emptyScopesAreRefused() {
    assertEquals(
        "document-scope.invalid",
        assertThrows(
                ValidationException.class, () -> new DocumentScope(TaxCategory.STANDARD, List.of()))
            .code());
  }
}
