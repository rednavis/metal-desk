package com.rednavis.metaldesk.pricingbridge.web;

import com.rednavis.metaldesk.pricingbridge.subscription.ReferencePriceBook;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * The latest reference price of each metal with the change since the observation before it (BRD
 * FR-1.1). {@code services/api}'s market-data cache is a client of this.
 */
@RestController
@RequestMapping("/api/reference-prices")
@RequiredArgsConstructor
public class ReferencePriceController {

  private static final int PERCENT_SCALE = 2;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final ReferencePriceBook book;

  /**
   * Lists the latest price per metal.
   *
   * <p>On a cold start the list is empty, and a metal seen once has no {@code change}.
   *
   * @return the prices, in metal order
   */
  @GetMapping
  public Flux<ReferencePriceView> prices() {
    return Flux.fromIterable(book.all()).map(ReferencePriceController::toView);
  }

  private static ReferencePriceView toView(ReferencePriceBook.Observation observation) {
    final ReferencePrice latest = observation.latest();
    return new ReferencePriceView(
        latest.metal(),
        latest.pricePerGram().amount().toPlainString(),
        latest.pricePerGram().currency().code(),
        latest.observedAt(),
        observation.previous().map(previous -> change(previous, latest)).orElse(null));
  }

  private static ReferencePriceView.ChangeView change(
      ReferencePrice previous, ReferencePrice latest) {
    final Money before = previous.pricePerGram();
    final Money difference = latest.pricePerGram().minus(before);
    final int sign = difference.amount().signum();
    final ReferencePriceView.Direction direction =
        sign > 0
            ? ReferencePriceView.Direction.UP
            : sign < 0 ? ReferencePriceView.Direction.DOWN : ReferencePriceView.Direction.UNCHANGED;
    final BigDecimal percent =
        Optional.of(before.amount())
            .filter(amount -> amount.signum() != 0)
            .map(
                amount ->
                    difference
                        .amount()
                        .abs()
                        .multiply(HUNDRED)
                        .divide(amount, PERCENT_SCALE, RoundingMode.HALF_UP))
            .orElse(BigDecimal.ZERO.setScale(PERCENT_SCALE));
    return new ReferencePriceView.ChangeView(
        direction, difference.amount().abs().toPlainString(), percent.toPlainString());
  }
}
