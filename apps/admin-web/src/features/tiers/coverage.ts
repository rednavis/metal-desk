import { compareDecimals } from "../../api/decimal";
import type { TierView } from "../../api/types";

/** Why one of two overlapping tiers is chosen for the orders both accept (Architecture section 3, T-016). */
export type TieBreak = "cheaper" | "faster" | "lower-id";

/** Two tiers that accept the same small orders, and which one wins them. */
export interface Overlap {
  winner: TierView;
  loser: TierView;
  /** The orders both accept: up to this value (before tax)... */
  upToValue: string;
  /** ...and up to this weight. */
  upToWeightGrams: string;
  because: TieBreak;
}

/** A range of order values for which heavier orders than `acceptedWeightGrams` have no tier. */
export interface UncoveredBand {
  /** Order values above this (exclusive)... */
  fromValue: string;
  /** ...up to this (inclusive). */
  toValue: string;
  /** Orders in the band are accepted up to this weight. */
  acceptedWeightGrams: string;
  /** Orders heavier than that, up to this weight, have no tier at all. */
  upToWeightGrams: string;
}

export interface Coverage {
  currency?: string;
  /** No tiers: every order to the region goes to a manager. */
  empty: boolean;
  /** The widest value and weight any tier accepts; orders above either go to a manager. */
  maxValue?: string;
  maxWeightGrams?: string;
  overlaps: Overlap[];
  uncoveredBands: UncoveredBand[];
}

/**
 * The tie-break the selector applies (`TierSelector`): the cheapest tier wins, then the faster one
 * (fewer maximum days, then fewer minimum days), then the lower id. It is written here once so that
 * the screen states the outcome the checkout will actually produce.
 */
function decide(a: TierView, b: TierView): { winner: TierView; because: TieBreak } {
  const price = compareDecimals(a.deliveryPrice, b.deliveryPrice);
  if (price !== 0) return { winner: price < 0 ? a : b, because: "cheaper" };
  if (a.maxDays !== b.maxDays) return { winner: a.maxDays < b.maxDays ? a : b, because: "faster" };
  if (a.minDays !== b.minDays) return { winner: a.minDays < b.minDays ? a : b, because: "faster" };
  return { winner: a.id <= b.id ? a : b, because: "lower-id" };
}

const lower = (a: string, b: string) => (compareDecimals(a, b) <= 0 ? a : b);
const higher = (a: string, b: string) => (compareDecimals(a, b) >= 0 ? a : b);

/**
 * What a region's tiers cover, for a person to read (BRD FR-5.1).
 *
 * A tier accepts orders up to its value ceiling and up to its weight ceiling, so each is a rectangle
 * starting at zero. Two things follow that a flat table hides:
 *
 * - **Overlaps.** All of a region's tiers accept the smallest orders, so any two overlap; what matters
 *   is which one the checkout picks there, so each pair is listed with the winner and the reason.
 * - **Gaps.** Where one tier is wide in value but light in weight and another the reverse, an order
 *   that is within the widest value *and* the widest weight can still fit neither. Those bands are
 *   listed. They are exactly the orders the selector sends to a manager although no single ceiling
 *   looks exceeded.
 *
 * Amounts are compared as exact decimals, in one currency (a region's tiers are expected to share
 * one; the server refuses a tier whose own amounts disagree).
 */
export function analyseCoverage(tiers: readonly TierView[]): Coverage {
  if (tiers.length === 0) return { empty: true, overlaps: [], uncoveredBands: [] };

  const sorted = [...tiers].sort(
    (a, b) => compareDecimals(a.valueCeiling, b.valueCeiling) || a.id.localeCompare(b.id),
  );
  const maxValue = sorted.map((tier) => tier.valueCeiling).reduce(higher);
  const maxWeightGrams = sorted.map((tier) => tier.weightGrams).reduce(higher);

  const overlaps: Overlap[] = [];
  for (let i = 0; i < sorted.length; i++) {
    for (let j = i + 1; j < sorted.length; j++) {
      const a = sorted[i];
      const b = sorted[j];
      if (!a || !b) continue;
      const { winner, because } = decide(a, b);
      overlaps.push({
        winner,
        loser: winner === a ? b : a,
        upToValue: lower(a.valueCeiling, b.valueCeiling),
        upToWeightGrams: lower(a.weightGrams, b.weightGrams),
        because,
      });
    }
  }

  // For orders worth up to each tier's value ceiling, the heaviest order any tier that wide accepts.
  const bands: UncoveredBand[] = [];
  let from = "0";
  for (const tier of sorted) {
    if (compareDecimals(tier.valueCeiling, from) <= 0) continue;
    const accepted = sorted
      .filter((other) => compareDecimals(other.valueCeiling, tier.valueCeiling) >= 0)
      .map((other) => other.weightGrams)
      .reduce(higher);
    const previous = bands[bands.length - 1];
    if (compareDecimals(accepted, maxWeightGrams) < 0) {
      if (previous && previous.acceptedWeightGrams === accepted && previous.toValue === from) {
        previous.toValue = tier.valueCeiling;
      } else {
        bands.push({
          fromValue: from,
          toValue: tier.valueCeiling,
          acceptedWeightGrams: accepted,
          upToWeightGrams: maxWeightGrams,
        });
      }
    }
    from = tier.valueCeiling;
  }

  return {
    currency: sorted[0]?.currency,
    empty: false,
    maxValue,
    maxWeightGrams,
    overlaps,
    uncoveredBands: bands,
  };
}
