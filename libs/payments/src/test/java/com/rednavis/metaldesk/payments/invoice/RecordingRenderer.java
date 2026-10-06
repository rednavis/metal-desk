package com.rednavis.metaldesk.payments.invoice;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** An {@link InvoiceRenderer} that records the content it is asked to draw, for tests. */
public final class RecordingRenderer implements InvoiceRenderer {

  private final List<InvoiceContent> seen = new ArrayList<>();

  @Override
  public InvoiceDocument render(InvoiceContent content) {
    seen.add(content);
    return new InvoiceDocument(
        content.number(),
        "doc-" + content.position() + ".pdf",
        content.scope().taxCategory(),
        content.locale(),
        "%PDF-test".getBytes(StandardCharsets.US_ASCII));
  }

  /**
   * Returns the content asked for so far.
   *
   * @return a copy of the rendered content, in order
   */
  public List<InvoiceContent> rendered() {
    return List.copyOf(seen);
  }
}
