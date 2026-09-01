package com.rednavis.metaldesk.payments.invoice;

/**
 * The port that turns an invoice's content into a document (BRD FR-6.2). It is a port, not a PDF
 * library call, so how a document is drawn can change without touching the provider, and the
 * provider can be tested without drawing anything.
 *
 * <p>An implementation must be a pure function of its input: the same {@link InvoiceContent} gives
 * the same bytes. In particular it renders in {@link InvoiceContent#locale()} and never reads the
 * server's default locale, the clock or any other ambient state, and it makes no network call.
 */
@FunctionalInterface
public interface InvoiceRenderer {

  /**
   * Renders one invoice document.
   *
   * @param content everything the document shows
   * @return the rendered document, never null
   */
  InvoiceDocument render(InvoiceContent content);
}
