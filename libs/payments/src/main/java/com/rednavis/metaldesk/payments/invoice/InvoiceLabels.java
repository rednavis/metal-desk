package com.rednavis.metaldesk.payments.invoice;

import java.util.Locale;

/**
 * The words on an invoice, per language. BRD section 9 requires generated documents in at least two
 * locales, with English and German as the illustrative pair.
 *
 * <p>The language is chosen from the locale it is given, never from the server: an unsupported
 * language gets English explicitly. A resource bundle is not used because its lookup falls back on
 * the JVM's default locale, which would make a document's language depend on where it runs.
 *
 * @param invoice the word for an invoice
 * @param order the word for an order
 * @param document the word for a document, as in "document 1 of 2"
 * @param of the word joining a position and a count
 * @param zeroRated the description of the zero-rated (investment-grade) scope
 * @param standardRated the description of the standard-rated scope
 * @param tax the word for tax
 * @param net the label for the net amount
 * @param delivery the label for delivery
 * @param total the label for the grand total
 */
record InvoiceLabels(
    String invoice,
    String order,
    String document,
    String of,
    String zeroRated,
    String standardRated,
    String tax,
    String net,
    String delivery,
    String total) {

  private static final InvoiceLabels ENGLISH =
      new InvoiceLabels(
          "Invoice",
          "Order",
          "Document",
          "of",
          "Zero-rated (investment-grade)",
          "Standard-rated",
          "Tax",
          "Net",
          "Delivery",
          "Total");

  private static final InvoiceLabels GERMAN =
      new InvoiceLabels(
          "Rechnung",
          "Bestellung",
          "Dokument",
          "von",
          "Steuerfrei (Anlagegold)",
          "Regelbesteuert",
          "Steuer",
          "Netto",
          "Lieferung",
          "Gesamt");

  /**
   * Chooses the labels for a locale by its language.
   *
   * @param locale the locale to render in
   * @return German for {@code de}, English for everything else
   */
  /* default */ static InvoiceLabels forLocale(Locale locale) {
    return "de".equals(locale.getLanguage()) ? GERMAN : ENGLISH;
  }
}
