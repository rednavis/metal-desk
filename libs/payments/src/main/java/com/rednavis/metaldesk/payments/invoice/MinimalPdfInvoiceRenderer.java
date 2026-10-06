package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@link InvoiceRenderer} for this phase: a valid, minimal PDF that lists the document's lines
 * and shows its totals, in the requested locale. It uses no PDF library; see {@link PdfWriter}.
 *
 * <p>The locale decides both the words ({@link InvoiceLabels}: English and German) and how amounts
 * are written, for example {@code 1,959.32} against {@code 1.959,32}, so two locales give two
 * different documents for the same order. Everything comes from the {@link InvoiceContent} it is
 * given: it reads no default locale and no clock, so the same content gives the same bytes.
 *
 * <p>Layout, branding and typography are out of scope for this phase.
 */
public final class MinimalPdfInvoiceRenderer implements InvoiceRenderer {

  /** Creates a renderer. It holds no state. */
  public MinimalPdfInvoiceRenderer() {
    // Stateless: nothing to configure.
  }

  @Override
  public InvoiceDocument render(InvoiceContent content) {
    final Locale locale = content.locale();
    final InvoiceLabels labels = InvoiceLabels.forLocale(locale);
    final NumberFormat currency = NumberFormat.getCurrencyInstance(locale);
    currency.setCurrency(
        java.util.Currency.getInstance(content.totals().grandTotal().currency().code()));
    final List<String> text = new ArrayList<>(header(content, labels));
    content.scope().lines().forEach(line -> text.addAll(lineText(line, labels, currency)));
    text.add("");
    text.addAll(totals(content, labels, currency));
    final String filename =
        "invoice-"
            + content.orderNumber().format()
            + "-"
            + content.position()
            + "-of-"
            + content.count()
            + ".pdf";
    return new InvoiceDocument(
        content.number(), filename, content.scope().taxCategory(), locale, PdfWriter.write(text));
  }

  private static List<String> header(InvoiceContent content, InvoiceLabels labels) {
    final String treatment =
        content.scope().taxCategory() == TaxCategory.INVESTMENT_GRADE
            ? labels.zeroRated()
            : labels.standardRated();
    return List.of(
        labels.invoice() + " " + content.number().value(),
        labels.order() + " " + content.orderNumber().format(),
        labels.document()
            + " "
            + content.position()
            + " "
            + labels.of()
            + " "
            + content.count()
            + " - "
            + treatment,
        "");
  }

  private static List<String> lineText(
      OrderLine line, InvoiceLabels labels, NumberFormat currency) {
    return List.of(
        line.productName(),
        "    "
            + line.quantity().value()
            + " x "
            + money(line.price().unitPrice(), currency)
            + " = "
            + money(line.lineNet(), currency)
            + " ("
            + labels.tax()
            + " "
            + money(line.lineTax(), currency)
            + ")");
  }

  private static List<String> totals(
      InvoiceContent content, InvoiceLabels labels, NumberFormat currency) {
    return List.of(
        labels.net() + ": " + money(content.totals().net(), currency),
        labels.tax() + ": " + money(content.totals().tax(), currency),
        labels.delivery() + ": " + money(content.totals().delivery(), currency),
        labels.total() + ": " + money(content.totals().grandTotal(), currency));
  }

  private static String money(Money amount, NumberFormat currency) {
    return currency.format(amount.amount());
  }
}
