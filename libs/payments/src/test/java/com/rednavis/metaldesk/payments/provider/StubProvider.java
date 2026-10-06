package com.rednavis.metaldesk.payments.provider;

import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import reactor.core.publisher.Mono;

/** A provider that gives one canned answer, and a synthetic intent to ask it with. */
public final class StubProvider implements PaymentProvider {

  private final ProviderCapability served;
  private final Mono<PaymentOutcome> answer;

  /**
   * Creates a provider serving card payments that always gives the same answer.
   *
   * @param answer what {@code authorise} and {@code confirm} return
   */
  public StubProvider(Mono<PaymentOutcome> answer) {
    this.served = new ProviderCapability("stub", Set.of(PaymentMethod.CARD));
    this.answer = answer;
  }

  /**
   * Builds a synthetic payment intent for a card payment.
   *
   * @return the intent
   */
  public static PaymentIntent intent() {
    return new PaymentIntent(
        new OrderId("o-1"),
        Money.of("4261.94", Currency.EUR),
        PaymentMethod.CARD,
        new CustomerId("c-1"),
        URI.create("https://shop.example/return"),
        URI.create("https://shop.example/cancel"),
        Locale.ENGLISH);
  }

  /**
   * Builds a provider reference.
   *
   * @return the reference
   */
  public static ProviderReference reference() {
    return new ProviderReference("ch_test_0001");
  }

  @Override
  public ProviderCapability capability() {
    return served;
  }

  @Override
  public Mono<PaymentOutcome> authorise(PaymentIntent intent) {
    return answer;
  }

  @Override
  public Mono<PaymentOutcome> confirm(ProviderReference reference) {
    return answer;
  }
}
