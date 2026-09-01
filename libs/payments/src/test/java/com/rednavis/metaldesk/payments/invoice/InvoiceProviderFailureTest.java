package com.rednavis.metaldesk.payments.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** What the invoice provider does when it cannot issue an invoice. */
class InvoiceProviderFailureTest {

  private static Order order() {
    return InvoiceFixtures.order(List.of(InvoiceFixtures.goldLine()));
  }

  @Test
  void anOrderThatDoesNotExistIsNotFound() {
    StepVerifier.create(
            new InvoiceProvider(
                    new InMemoryOrders(null), new RecordingRenderer(), new RecordingSink(false))
                .authorise(InvoiceIntents.invoice(Locale.ENGLISH)))
        .expectErrorSatisfies(
            error -> {
              assertTrue(error instanceof NotFoundException);
              assertEquals("invoice.order-not-found", ((NotFoundException) error).code());
            })
        .verify();
  }

  @Test
  void anyMethodOtherThanInvoiceIsRefused() {
    final RecordingSink sink = new RecordingSink(false);
    StepVerifier.create(
            new InvoiceProvider(new InMemoryOrders(order()), new RecordingRenderer(), sink)
                .authorise(InvoiceIntents.of(Locale.ENGLISH, PaymentMethod.CARD)))
        .expectErrorSatisfies(
            error -> {
              assertTrue(error instanceof ValidationException);
              assertEquals("invoice.method-unsupported", ((ValidationException) error).code());
            })
        .verify();
    assertTrue(sink.received().isEmpty());
  }

  @Test
  void failingSinkFailsTheIssueAndNoInvoiceIsReportedIssued() {
    StepVerifier.create(
            new InvoiceProvider(
                    new InMemoryOrders(order()), new RecordingRenderer(), new RecordingSink(true))
                .authorise(InvoiceIntents.invoice(Locale.ENGLISH)))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void missingReferenceIsRefusedOnConfirm() {
    StepVerifier.create(
            new InvoiceProvider(
                    new InMemoryOrders(order()), new RecordingRenderer(), new RecordingSink(false))
                .confirm(null))
        .expectErrorSatisfies(
            error ->
                assertEquals("invoice.reference-missing", ((ValidationException) error).code()))
        .verify();
  }
}
