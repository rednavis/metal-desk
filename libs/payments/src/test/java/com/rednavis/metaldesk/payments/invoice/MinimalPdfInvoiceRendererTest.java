package com.rednavis.metaldesk.payments.invoice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.order.OrderTotalsCalculator;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MinimalPdfInvoiceRendererTest {

  private static final Charset WIN_ANSI = Charset.forName("windows-1252");
  private static final OrderNumber ORDER_NUMBER = new OrderNumber(LocalDate.of(2022, 2, 8), 4);

  private final MinimalPdfInvoiceRenderer renderer = new MinimalPdfInvoiceRenderer();

  private static InvoiceContent content(List<OrderLine> lines, Locale locale) {
    final DocumentScope scope = new DocumentScope(lines.get(0).taxCategory(), lines);
    return new InvoiceContent(
        InvoiceNumber.forOrder(ORDER_NUMBER),
        ORDER_NUMBER,
        scope,
        OrderTotalsCalculator.compute(lines, InvoiceFixtures.eur("25.00")),
        locale,
        1,
        1);
  }

  private static InvoiceContent goldContent(Locale locale) {
    return content(List.of(InvoiceFixtures.goldLine()), locale);
  }

  private static String text(InvoiceDocument document) {
    return new String(document.bytes(), WIN_ANSI);
  }

  @Test
  void showsTheDocumentsTotalsInEnglish() {
    final String pdf = text(renderer.render(goldContent(Locale.ENGLISH)));
    assertTrue(pdf.contains("Invoice INV-080220220004"));
    assertTrue(pdf.contains("3,943.64"), "grand total: 3918.64 net + 0.00 tax + 25.00 delivery");
    assertTrue(pdf.contains("Gold bar 1 oz"));
  }

  @Test
  void twoLocalesGiveTwoDifferentDocumentsForTheSameOrder() {
    final InvoiceDocument english = renderer.render(goldContent(Locale.ENGLISH));
    final InvoiceDocument german = renderer.render(goldContent(Locale.GERMAN));
    assertFalse(java.util.Arrays.equals(english.bytes(), german.bytes()));
    assertTrue(text(german).contains("Rechnung INV-080220220004"));
    assertTrue(text(german).contains("3.943,64"));
    assertTrue(text(english).contains("3,943.64"));
    assertEquals(Locale.GERMAN, german.locale());
  }

  @Test
  void languageWithNoLabelsFallsBackToEnglishExplicitly() {
    assertTrue(text(renderer.render(goldContent(Locale.FRENCH))).contains("Invoice INV-"));
  }

  @Test
  void theSameContentGivesTheSameBytes() {
    assertArrayEquals(
        renderer.render(goldContent(Locale.GERMAN)).bytes(),
        renderer.render(goldContent(Locale.GERMAN)).bytes());
  }

  @Test
  void theFileNameNamesTheOrderAndPosition() {
    assertEquals(
        "invoice-080220220004-1-of-1.pdf", renderer.render(goldContent(Locale.ENGLISH)).filename());
  }
}
