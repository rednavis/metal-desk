package com.rednavis.metaldesk.admin.order;

import com.rednavis.metaldesk.admin.order.dto.BoundCeiling;
import com.rednavis.metaldesk.admin.order.dto.OrderDetailView.HandoffContext;
import com.rednavis.metaldesk.admin.persistence.ProductRepository;
import com.rednavis.metaldesk.admin.persistence.TierRepository;
import com.rednavis.metaldesk.persistence.document.ProductDocument;
import com.rednavis.metaldesk.persistence.mapper.FulfillmentTierMapper;
import com.rednavis.metaldesk.persistence.mapper.ValueMapper;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;
import com.rednavis.metaldesk.share.domain.fulfillment.TierEvaluation;
import com.rednavis.metaldesk.share.domain.fulfillment.TierSelector;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Works out the context a manager needs to price an order awaiting a quote (BRD FR-5.3): its weight
 * and which ceiling of the destination region it exceeds.
 *
 * <p>Neither is stored on the order, so both are derived when the order is shown, with the same
 * {@link TierSelector} that checkout used and the tiers as they are now. That is deliberate: what
 * staff need to know is what the tiers say today, and a tier widened since the handoff shows up as
 * {@link BoundCeiling#WITHIN_TIERS} instead of a stale reason.
 */
@Component
@RequiredArgsConstructor
public class HandoffContexts {

  private final ProductRepository products;
  private final TierRepository tiers;
  private final FulfillmentTierMapper tierMapper;
  private final Clock clock;

  /**
   * Builds the context of an order.
   *
   * @param order the order
   * @return its region, weight and bound ceiling; the last two are absent if a product is gone
   */
  public HandoffContext of(Order order) {
    final Region region = order.deliveryAddress().country();
    final Optional<Weight> weight = weight(order.lines());
    return new HandoffContext(
        region.code(),
        weight.map(found -> found.amount().toPlainString()).orElse(null),
        weight.map(found -> ceiling(order, region, found)).orElse(null));
  }

  private BoundCeiling ceiling(Order order, Region region, Weight weight) {
    final List<FulfillmentTier> configured =
        tiers.findByRegion(region.code()).stream().map(tierMapper::toDomain).toList();
    final TierEvaluation evaluation =
        TierSelector.select(region, order.totals().net(), weight, configured, clock.instant());
    return switch (evaluation) {
      case TierEvaluation.Priced _ -> BoundCeiling.WITHIN_TIERS;
      case TierEvaluation.NoTier _ -> BoundCeiling.NO_TIER_FOR_REGION;
      case TierEvaluation.ExceedsCeiling exceeded ->
          switch (exceeded.which()) {
            case VALUE -> BoundCeiling.VALUE;
            case WEIGHT -> BoundCeiling.WEIGHT;
          };
    };
  }

  private Optional<Weight> weight(List<OrderLine> lines) {
    final Map<String, ProductDocument> found =
        products.findAllById(lines.stream().map(line -> line.productId().value()).toList()).stream()
            .collect(Collectors.toMap(ProductDocument::id, Function.identity()));
    final boolean complete =
        lines.stream().allMatch(line -> found.containsKey(line.productId().value()));
    BigDecimal grams = BigDecimal.ZERO;
    for (final OrderLine line : lines) {
      final ProductDocument product = found.get(line.productId().value());
      if (product != null) {
        final Weight each = ValueMapper.weightToDomain(product.weight()).toCanonical();
        grams = grams.add(each.amount().multiply(line.quantity().asFactor()));
      }
    }
    return complete ? Optional.of(new Weight(grams, WeightUnit.GRAM)) : Optional.empty();
  }
}
