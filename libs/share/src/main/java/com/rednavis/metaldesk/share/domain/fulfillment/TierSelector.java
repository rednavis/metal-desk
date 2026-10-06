package com.rednavis.metaldesk.share.domain.fulfillment;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Decides which fulfillment tier prices an order, or that none can (BRD FR-5.1 to FR-5.3, BR-8).
 *
 * <p><strong>A pure function over supplied configuration.</strong> The tiers are a parameter; this
 * class loads nothing, keeps nothing and has no clock. That is what makes "the checkout evaluates
 * against whatever is currently configured" true, and lets the admin API change tiers without a
 * deploy. The caller supplies the instant of the quote.
 *
 * <p><strong>The value is ex-tax.</strong> FR-5.1 and BR-8 evaluate the order value before tax;
 * using the gross total would change outcomes near a ceiling and create a circular dependency
 * between tax and delivery. The parameter is named {@code exTaxValue} so it cannot be misread.
 *
 * <p><strong>Multiple tiers (a BRD gap).</strong> FR-5.1 allows several tiers per region but does
 * not say how to choose among those that qualify. The rule here is: <em>the cheapest delivery price
 * wins</em>; ties go to the faster tier (lower maximum, then lower minimum days), and any remaining
 * tie to the lower tier id, so the result never depends on the order of the list. This needs
 * confirming, because a different rule is a customer-visible price difference.
 *
 * <p><strong>Both ceilings exceeded (a BRD gap).</strong> When no tier qualifies, {@link
 * TierEvaluation.ExceedsCeiling} names the region's most permissive tier (highest value ceiling,
 * then highest weight ceiling, then lower id) and which of its ceilings was exceeded. If both were,
 * the value ceiling takes precedence: {@link CeilingKind#VALUE}. FR-5.3 says "whichever binds
 * first" without saying how to order two that both bind.
 *
 * <p>Order value and tiers must be in one currency; mixing them throws rather than comparing.
 */
public final class TierSelector {

  private static final Comparator<FulfillmentTier> CHEAPEST_FIRST =
      Comparator.comparing(FulfillmentTier::deliveryPrice)
          .thenComparingInt(tier -> tier.transit().maxDays())
          .thenComparingInt(tier -> tier.transit().minDays())
          .thenComparing(tier -> tier.id().value());

  private static final Comparator<FulfillmentTier> MOST_PERMISSIVE =
      Comparator.comparing(FulfillmentTier::valueCeiling)
          .thenComparing(FulfillmentTier::weightCeiling)
          .thenComparing(tier -> tier.id().value(), Comparator.reverseOrder());

  private TierSelector() {}

  /**
   * Evaluates an order against the configured tiers.
   *
   * @param region the destination region
   * @param exTaxValue the order value <em>before tax</em>
   * @param weight the order weight
   * @param configured the tiers currently configured, for every region
   * @param quotedAt when the evaluation is made, recorded on the quote
   * @return {@link TierEvaluation.Priced} when a tier in the region is within both ceilings, {@link
   *     TierEvaluation.ExceedsCeiling} when the region has tiers but none is, and {@link
   *     TierEvaluation.NoTier} when it has none
   * @throws ValidationException if an argument is null, the list holds a null, the order value is
   *     negative, or amounts are in different currencies
   */
  public static TierEvaluation select(
      Region region,
      Money exTaxValue,
      Weight weight,
      List<FulfillmentTier> configured,
      Instant quotedAt) {
    requireInputs(region, exTaxValue, weight, configured, quotedAt);
    final List<FulfillmentTier> inRegion =
        configured.stream().filter(tier -> tier.region().equals(region)).toList();
    return inRegion.isEmpty()
        ? new TierEvaluation.NoTier(region)
        : evaluate(inRegion, exTaxValue, weight, quotedAt);
  }

  private static TierEvaluation evaluate(
      List<FulfillmentTier> inRegion, Money exTaxValue, Weight weight, Instant quotedAt) {
    return inRegion.stream()
        .filter(tier -> tier.accepts(exTaxValue, weight))
        .min(CHEAPEST_FIRST)
        .<TierEvaluation>map(tier -> new TierEvaluation.Priced(DeliveryQuote.from(tier, quotedAt)))
        .orElseGet(() -> exceeded(inRegion, exTaxValue));
  }

  private static TierEvaluation exceeded(List<FulfillmentTier> inRegion, Money exTaxValue) {
    final FulfillmentTier widest = inRegion.stream().max(MOST_PERMISSIVE).orElseThrow();
    return new TierEvaluation.ExceedsCeiling(
        widest.exceedsValue(exTaxValue) ? CeilingKind.VALUE : CeilingKind.WEIGHT, widest);
  }

  private static void requireInputs(
      Region region,
      Money exTaxValue,
      Weight weight,
      List<FulfillmentTier> configured,
      Instant quotedAt) {
    if (anyNull(region, exTaxValue, weight, configured, quotedAt)
        || configured.stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(
          "tier-selector.input-missing",
          "Tier selection requires a region, value, weight, tiers and instant, and no null tier");
    }
    if (exTaxValue.isNegative()) {
      throw new ValidationException(
          "tier-selector.value-negative", "Order value must not be negative");
    }
  }

  private static boolean anyNull(Object... values) {
    return Arrays.stream(values).anyMatch(Objects::isNull);
  }
}
