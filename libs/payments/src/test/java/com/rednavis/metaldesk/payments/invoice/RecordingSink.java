package com.rednavis.metaldesk.payments.invoice;

import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

/** An {@link InvoiceSink} that records what it receives, or fails on request, for tests. */
public final class RecordingSink implements InvoiceSink {

  private final List<InvoiceDocument> accepted = new ArrayList<>();
  private final boolean failing;

  /**
   * Creates a sink.
   *
   * @param failing whether {@code publish} should fail
   */
  public RecordingSink(boolean failing) {
    this.failing = failing;
  }

  @Override
  public Mono<Void> publish(InvoiceNumber number, List<InvoiceDocument> documents) {
    return failing
        ? Mono.error(new IllegalStateException("sink refused the documents"))
        : Mono.fromRunnable(() -> accepted.addAll(documents));
  }

  /**
   * Returns the documents received so far.
   *
   * @return a copy of the received documents
   */
  public List<InvoiceDocument> received() {
    return List.copyOf(accepted);
  }
}
