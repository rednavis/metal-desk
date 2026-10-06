package com.rednavis.metaldesk.api.marketdata;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/** Builds the reference-price panel from the {@link ReferencePriceCache}. */
@Service
@RequiredArgsConstructor
public class MarketDataService {

  private static final int PERCENT_SCALE = 2;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final ReferencePriceCache cache;

  /**
   * Lists the panel rows.
   *
   * @return one row per metal with an observation
   */
  public Flux<ReferencePriceView> prices() {
    return Flux.fromIterable(cache.all()).map(MarketDataService::toView);
  }

  private static ReferencePriceView toView(ReferencePriceCache.Observation observation) {
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
