package com.rednavis.metaldesk.admin.tier;

import com.rednavis.metaldesk.admin.persistence.TierRepository;
import com.rednavis.metaldesk.admin.tier.dto.TierRemoval;
import com.rednavis.metaldesk.admin.tier.dto.TierRequest;
import com.rednavis.metaldesk.admin.tier.dto.TierResult;
import com.rednavis.metaldesk.admin.tier.dto.TierView;
import com.rednavis.metaldesk.admin.tier.dto.TierWarning;
import com.rednavis.metaldesk.persistence.document.FulfillmentTierDocument;
import com.rednavis.metaldesk.persistence.mapper.FulfillmentTierMapper;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;
import com.rednavis.metaldesk.share.domain.fulfillment.TierSelector;
import com.rednavis.metaldesk.share.domain.fulfillment.TransitTime;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Creates, changes and removes fulfillment tiers, and tells staff what a change means for checkout.
 *
 * <p><strong>Validation is the domain's.</strong> {@link FulfillmentTier} refuses a non-positive
 * ceiling, a negative price and a currency mismatch, {@link TransitTime} a transit below one day or
 * inverted, and {@link Region} a malformed code; each comes back as a 400 with that rule's own
 * code. This service only refuses what the domain cannot see: a request with a field missing.
 *
 * <p><strong>Overlapping tiers are allowed, and resolved by the rule of T-016.</strong> A tier's
 * ceilings are upper bounds counted from zero, so any two tiers of a region overlap by
 * construction; what matters is which one checkout picks. {@link TierSelector} picks the cheapest
 * qualifying tier, then the faster one, then the lower id, never the insertion order, so the price
 * is the same whatever order tiers were entered in. Rejecting overlaps would forbid every second
 * tier. Instead, when a saved tier can never be chosen because another tier in its region beats it
 * at its own ceilings, the response carries a {@code tier.shadowed} warning naming that tier.
 *
 * <p><strong>Losing coverage is a warning, not a refusal.</strong> Removing or narrowing the last
 * tier of a region sends every order there to manager handoff (FR-5.3), which staff may intend but
 * must not stumble into; the response says so with {@code region.uncovered} or {@code
 * region.narrowed}.
 */
@Service
@RequiredArgsConstructor
public class TierService {

  private final TierRepository tiers;
  private final FulfillmentTierMapper mapper;
  private final Clock clock;

  /**
   * Lists the tiers, of one region or of all.
   *
   * @param region a region code to filter by, or null for every tier
   * @return the tiers ordered by region, then delivery price, then id
   * @throws ValidationException if the region code is malformed
   */
  public List<TierView> list(String region) {
    final List<FulfillmentTierDocument> found =
        region == null ? tiers.findAll() : tiers.findByRegion(new Region(region).code());
    return found.stream()
        .map(mapper::toDomain)
        .sorted(
            Comparator.comparing((FulfillmentTier tier) -> tier.region().code())
                .thenComparing(FulfillmentTier::deliveryPrice)
                .thenComparing(tier -> tier.id().value()))
        .map(TierViews::of)
        .toList();
  }

  /**
   * Reads one tier.
   *
   * @param id the tier id
   * @return the tier
   * @throws NotFoundException if there is no such tier
   */
  public TierView get(String id) {
    return TierViews.of(require(id));
  }

  /**
   * Creates a tier.
   *
   * @param request the tier
   * @return the stored tier and what it implies
   * @throws ValidationException if the request is incomplete or violates a tier rule
   */
  public TierResult create(TierRequest request) {
    final FulfillmentTier tier =
        build(new FulfillmentTierId(UUID.randomUUID().toString()), request);
    tiers.save(mapper.toDocument(tier));
    return new TierResult(TierViews.of(tier), shadowWarnings(tier));
  }

  /**
   * Replaces a tier.
   *
   * @param id the tier to replace
   * @param request its new description
   * @return the stored tier and what the change implies
   * @throws NotFoundException if there is no such tier
   * @throws ValidationException if the request is incomplete or violates a tier rule
   */
  public TierResult update(String id, TierRequest request) {
    final FulfillmentTier before = require(id);
    final FulfillmentTier after = build(before.id(), request);
    final List<FulfillmentTier> others = othersInRegion(before);
    tiers.save(mapper.toDocument(after));
    final List<TierWarning> warnings = new ArrayList<>();
    if (before.region().equals(after.region())) {
      if (others.isEmpty() && TierWarnings.narrower(after, before)) {
        warnings.add(TierWarnings.narrowed(after.region()));
      }
    } else if (others.isEmpty()) {
      warnings.add(TierWarnings.uncovered(before.region()));
    }
    warnings.addAll(shadowWarnings(after));
    return new TierResult(TierViews.of(after), warnings);
  }

  /**
   * Removes a tier.
   *
   * @param id the tier to remove
   * @return what was removed and what that implies
   * @throws NotFoundException if there is no such tier
   */
  public TierRemoval delete(String id) {
    final FulfillmentTier tier = require(id);
    final boolean last = othersInRegion(tier).isEmpty();
    tiers.deleteById(id);
    return new TierRemoval(
        id, last ? List.of(TierWarnings.uncovered(tier.region())) : List.<TierWarning>of());
  }

  private FulfillmentTier require(String id) {
    return tiers
        .findById(id)
        .map(mapper::toDomain)
        .orElseThrow(() -> new NotFoundException("tier.not-found", "No tier " + id));
  }

  private List<FulfillmentTier> othersInRegion(FulfillmentTier tier) {
    return tiers.findByRegion(tier.region().code()).stream()
        .map(mapper::toDomain)
        .filter(other -> !other.id().equals(tier.id()))
        .toList();
  }

  private static FulfillmentTier build(FulfillmentTierId id, TierRequest request) {
    if (request == null
        || request.region() == null
        || request.valueCeiling() == null
        || request.weightCeiling() == null
        || request.weightUnit() == null
        || request.currency() == null
        || request.deliveryPrice() == null) {
      throw new ValidationException(
          "tier.incomplete",
          "A tier needs a region, value and weight ceilings, a currency and a delivery price");
    }
    return new FulfillmentTier(
        id,
        new Region(request.region()),
        Money.of(request.valueCeiling(), request.currency()),
        Weight.of(request.weightCeiling(), request.weightUnit()),
        Money.of(request.deliveryPrice(), request.currency()),
        new TransitTime(request.minDays(), request.maxDays()));
  }

  private List<TierWarning> shadowWarnings(FulfillmentTier tier) {
    final List<FulfillmentTier> inRegion = new ArrayList<>(othersInRegion(tier));
    inRegion.add(tier);
    return TierWarnings.shadowed(
        tier,
        TierSelector.select(
            tier.region(), tier.valueCeiling(), tier.weightCeiling(), inRegion, clock.instant()));
  }
}
