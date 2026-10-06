package com.rednavis.metaldesk.payments.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.payments.gateway.GatewayConfiguration;
import com.rednavis.metaldesk.payments.gateway.GatewayProvider;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.payments.wallet.WalletConfiguration;
import com.rednavis.metaldesk.payments.wallet.WalletProvider;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentStatus;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

class InvoiceProviderTest {

  private static final ProviderReference INVOICE_REFERENCE =
      new ProviderReference("INV-080220220004");

  private static Order mixedOrder() {
    return InvoiceFixtures.order(List.of(InvoiceFixtures.goldLine(), InvoiceFixtures.silverLine()));
  }

  private static InvoiceProvider provider(
      Order order, RecordingRenderer renderer, RecordingSink sink) {
    return new InvoiceProvider(new InMemoryOrders(order), renderer, sink);
  }

  @Test
  void servesOnlyInvoiceAndOverlapsNeitherTheGatewayNorTheWallet() {
    final InvoiceProvider provider =
        provider(null, new RecordingRenderer(), new RecordingSink(false));
    final Set<PaymentMethod> invoice = provider.capability().methods();
    final URI base = URI.create("https://provider.example");
    final Set<PaymentMethod> gateway =
        new GatewayProvider(GatewayConfiguration.withDefaults(base)).capability().methods();
    final Set<PaymentMethod> wallet =
        new WalletProvider(WalletConfiguration.withDefaults(base)).capability().methods();
    assertEquals(Set.of(PaymentMethod.INVOICE), invoice);
    assertTrue(invoice.stream().noneMatch(gateway::contains));
    assertTrue(invoice.stream().noneMatch(wallet::contains));
    assertTrue(provider.supports(PaymentMethod.INVOICE));
  }

  @Test
  void mixedOrderIsOneInvoiceOfTwoDocuments() {
    final RecordingSink sink = new RecordingSink(false);
    StepVerifier.create(
            provider(mixedOrder(), new RecordingRenderer(), sink)
                .authorise(InvoiceIntents.invoice(Locale.ENGLISH)))
        .expectNext(new PaymentOutcome.DocumentIssued(INVOICE_REFERENCE))
        .verifyComplete();
    assertEquals(2, sink.received().size());
    assertEquals(TaxCategory.INVESTMENT_GRADE, sink.received().get(0).scope());
    assertEquals(TaxCategory.STANDARD, sink.received().get(1).scope());
  }

  @Test
  void singleCategoryOrderIsOneDocument() {
    final RecordingSink sink = new RecordingSink(false);
    final Order order = InvoiceFixtures.order(List.of(InvoiceFixtures.silverLine()));
    StepVerifier.create(
            provider(order, new RecordingRenderer(), sink)
                .authorise(InvoiceIntents.invoice(Locale.ENGLISH)))
        .expectNext(new PaymentOutcome.DocumentIssued(INVOICE_REFERENCE))
        .verifyComplete();
    assertEquals(1, sink.received().size());
  }

  @Test
  void anInvoiceIsPendingNotCaptured() {
    StepVerifier.create(
            provider(mixedOrder(), new RecordingRenderer(), new RecordingSink(false))
                .authorise(InvoiceIntents.invoice(Locale.ENGLISH)))
        .expectNextMatches(
            outcome ->
                outcome.status() == PaymentStatus.PENDING
                    && outcome.status() != PaymentStatus.CAPTURED)
        .verifyComplete();
  }

  @Test
  void documentTotalsAddUpToTheOrderTotalWithDeliveryOnTheFirstOnly() {
    final RecordingRenderer renderer = new RecordingRenderer();
    final Order order = mixedOrder();
    StepVerifier.create(
            provider(order, renderer, new RecordingSink(false))
                .authorise(InvoiceIntents.invoice(Locale.ENGLISH)))
        .expectNextCount(1)
        .verifyComplete();
    final List<InvoiceContent> contents = renderer.rendered();
    final Money sum =
        contents.get(0).totals().grandTotal().plus(contents.get(1).totals().grandTotal());
    assertEquals(order.totals().grandTotal(), sum);
    assertEquals(InvoiceFixtures.eur("25.00"), contents.get(0).totals().delivery());
    assertEquals(InvoiceFixtures.eur("0.00"), contents.get(1).totals().delivery());
    assertEquals(InvoiceFixtures.eur("3943.64"), contents.get(0).totals().grandTotal());
    assertEquals(InvoiceFixtures.eur("318.30"), contents.get(1).totals().grandTotal());
  }

  @ParameterizedTest
  @ValueSource(strings = {"de", "en"})
  void theLocaleComesFromTheIntentAndNowhereElse(String tag) {
    final Locale locale = Locale.forLanguageTag(tag);
    final RecordingRenderer renderer = new RecordingRenderer();
    StepVerifier.create(
            provider(mixedOrder(), renderer, new RecordingSink(false))
                .authorise(InvoiceIntents.invoice(locale)))
        .expectNextCount(1)
        .verifyComplete();
    assertTrue(renderer.rendered().stream().allMatch(content -> content.locale().equals(locale)));
  }

  @Test
  void confirmReportsTheSameIssuedInvoiceStillPending() {
    StepVerifier.create(
            provider(null, new RecordingRenderer(), new RecordingSink(false))
                .confirm(INVOICE_REFERENCE))
        .expectNextMatches(
            outcome ->
                outcome.equals(new PaymentOutcome.DocumentIssued(INVOICE_REFERENCE))
                    && outcome.status() == PaymentStatus.PENDING)
        .verifyComplete();
  }
}
