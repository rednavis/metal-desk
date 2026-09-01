package com.rednavis.metaldesk.payments.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.test.StepVerifier;

/** Proof that the invoice provider calls nothing: it is the one provider with no gateway. */
class InvoiceNoHttpTest {

  /** A server with no stubs at all: any request to it would be an unmatched one. */
  @RegisterExtension
  static final WireMockExtension STUBLESS =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private static List<Class<?>> dependenciesOf(Class<?> type) {
    final List<Class<?>> found = new ArrayList<>();
    for (final Field field : type.getDeclaredFields()) {
      found.add(field.getType());
    }
    for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
      found.addAll(List.of(constructor.getParameterTypes()));
    }
    return found;
  }

  @Test
  void issuingAnInvoiceSendsNoRequestAnywhere() {
    final InvoiceProvider provider =
        new InvoiceProvider(
            new InMemoryOrders(
                InvoiceFixtures.order(
                    List.of(InvoiceFixtures.goldLine(), InvoiceFixtures.silverLine()))),
            new MinimalPdfInvoiceRenderer(),
            new RecordingSink(false));
    StepVerifier.create(provider.authorise(InvoiceIntents.invoice(Locale.GERMAN)))
        .expectNext(new PaymentOutcome.DocumentIssued(new ProviderReference("INV-080220220004")))
        .verifyComplete();
    assertEquals(0, STUBLESS.getAllServeEvents().size());
    assertEquals(0, STUBLESS.findAllUnmatchedRequests().size());
  }

  @Test
  void theInvoiceClassesHoldNoHttpClientOrEndpoint() {
    for (final Class<?> type :
        List.of(InvoiceProvider.class, MinimalPdfInvoiceRenderer.class, InvoiceSplitter.class)) {
      for (final Class<?> dependency : dependenciesOf(type)) {
        assertFalse(dependency.getName().startsWith("java.net.http"), type.getSimpleName());
        assertFalse(
            dependency.getName().startsWith("com.rednavis.metaldesk.payments.http"),
            type.getSimpleName());
        assertFalse(dependency.getName().contains("HttpClient"), type.getSimpleName());
      }
    }
  }
}
