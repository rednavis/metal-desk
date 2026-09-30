package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.cart.CartLine;
import com.rednavis.metaldesk.api.cart.CartPricing;
import com.rednavis.metaldesk.api.cart.PricedLine;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionService;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.api.persistence.mapper.FulfillmentTierMapper;
import com.rednavis.metaldesk.api.persistence.repository.FulfillmentTierRepository;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.fulfillment.TierSelector;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Evaluates a checkout's delivery against the tiers staff have configured <em>right now</em> (BRD
 * FR-5.1 to FR-5.3, Architecture section 3: tiers are data, evaluated live).
 *
 * <p>Every call re-prices the basket, reads the destination region's tiers from the database and
 * hands them to the pure {@link TierSelector}; nothing is cached for the life of the session, so a
 * tier a staff member edits mid-checkout takes effect on the next evaluation. The three inputs are
 * the destination region, the order value <strong>before tax</strong> and the total weight; the
 * outcome is recorded on the session as its {@link CheckoutStage}:
 *
 * <ul>
 *   <li>a tier accepts the order: {@code PAYMENT_ALLOWED} with the tier's quote, insurance folded
 *       into its price (BR-7);
 *   <li>the region's widest tier is exceeded: {@code HANDOFF_REQUIRED}, naming which ceiling bound
 *       (value takes precedence when both did);
 *   <li>no tier for the region: {@code HANDOFF_REQUIRED} with its own reason. It is never a
 *       zero-cost quote, which would ship metal for free.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DeliveryEvaluationService {

  private final CheckoutSessionService sessions;
  private final CheckoutSessionStore store;
  private final CartPricing pricing;
  private final BasketMetrics metrics;
  private final FulfillmentTierRepository tiers;
  private final FulfillmentTierMapper tierMapper;
  private final Clock clock;

  /**
   * Evaluates the delivery of a session.
   *
   * <p>A session already handed to staff is returned as it is: its order exists and a manager owns
   * the price.
   *
   * @param id the session's id
   * @param customer the signed-in customer, or null for a guest
   * @return the session with its delivery evaluation
   * @throws ConflictException {@code checkout.step1-incomplete} before step 1, or {@code
   *     checkout.basket-unpriced} if a line has no price at the moment
   */
  public Mono<CheckoutSession> evaluate(String id, AuthenticatedCustomer customer) {
    return sessions
        .find(id, customer)
        .flatMap(
            session -> session.handoff().isPresent() ? Mono.just(session) : evaluateLive(session));
  }

  private Mono<CheckoutSession> evaluateLive(CheckoutSession session) {
    final Region region =
        session
            .details()
            .map(details -> details.deliveryAddress().country())
            .orElseThrow(
                () ->
                    new ConflictException(
                        "checkout.step1-incomplete",
                        "Complete the customer and delivery details first"));
    return pricing
        .price(
            session.lines().stream()
                .map(line -> new CartLine(line.productId(), line.quantity()))
                .toList())
        .flatMap(DeliveryEvaluationService::priceableLines)
        .flatMap(lines -> evaluateLines(session.id(), region, lines));
  }

  private Mono<CheckoutSession> evaluateLines(String id, Region region, List<OrderLine> lines) {
    final Money exTaxValue = metrics.exTaxValue(lines);
    return Mono.zip(
            metrics.weight(lines),
            tiers.findByRegion(region.code()).map(tierMapper::toDomain).collectList())
        .flatMap(
            found -> {
              final Instant now = clock.instant();
              final DeliveryState state =
                  DeliveryDecision.of(
                      TierSelector.select(region, exTaxValue, found.getT1(), found.getT2(), now),
                      exTaxValue,
                      found.getT1(),
                      now);
              return store.update(id, current -> current.withDelivery(lines, state));
            });
  }

  private static Mono<List<OrderLine>> priceableLines(List<PricedLine> priced) {
    return priced.stream().anyMatch(line -> line.priced().isEmpty())
        ? Mono.error(
            new ConflictException(
                "checkout.basket-unpriced", "Some items have no price at the moment"))
        : Mono.just(priced.stream().flatMap(line -> line.priced().stream()).toList());
  }
}
