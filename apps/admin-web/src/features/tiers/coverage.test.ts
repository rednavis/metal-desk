import { describe, expect, it } from "vitest";
import { tier } from "../../test/adminServer";
import { analyseCoverage } from "./coverage";

describe("what a region's tiers cover", () => {
  it("says a region with no tier goes entirely to a manager", () => {
    expect(analyseCoverage([])).toMatchObject({ empty: true, uncoveredBands: [], overlaps: [] });
  });

  it("names the cheaper of two overlapping tiers as the winner", () => {
    const { overlaps } = analyseCoverage([
      tier({ id: "standard", region: "DE", deliveryPrice: "15.00" }),
      tier({ id: "economy", region: "DE", deliveryPrice: "9.00", maxDays: 7 }),
    ]);

    expect(overlaps).toHaveLength(1);
    expect(overlaps[0]).toMatchObject({
      because: "cheaper",
      upToValue: "5000.00",
      upToWeightGrams: "2500",
    });
    expect(overlaps[0]?.winner.id).toBe("economy");
  });

  it("breaks an equal price by speed, then by the lower id", () => {
    const faster = analyseCoverage([
      tier({ id: "slow", region: "DE", maxDays: 9 }),
      tier({ id: "quick", region: "DE", maxDays: 3 }),
    ]).overlaps[0];
    const byId = analyseCoverage([tier({ id: "b", region: "DE" }), tier({ id: "a", region: "DE" })])
      .overlaps[0];

    expect([faster?.winner.id, faster?.because]).toEqual(["quick", "faster"]);
    expect([byId?.winner.id, byId?.because]).toEqual(["a", "lower-id"]);
  });

  it("restricts an overlap to what both accept", () => {
    const { overlaps } = analyseCoverage([
      tier({ id: "small", region: "DE", valueCeiling: "500.00", weightGrams: "1000" }),
      tier({
        id: "large",
        region: "DE",
        valueCeiling: "9000.00",
        weightGrams: "8000",
        deliveryPrice: "30.00",
      }),
    ]);

    expect(overlaps[0]).toMatchObject({ upToValue: "500.00", upToWeightGrams: "1000" });
  });

  it("finds orders under the widest ceilings that no single tier accepts", () => {
    // One tier is wide in value but light, the other heavy but low in value: a 1000 EUR, 5 kg order fits neither.
    const coverage = analyseCoverage([
      tier({ id: "rich", region: "DE", valueCeiling: "5000.00", weightGrams: "1000" }),
      tier({ id: "heavy", region: "DE", valueCeiling: "500.00", weightGrams: "10000" }),
    ]);

    expect(coverage.maxValue).toBe("5000.00");
    expect(coverage.maxWeightGrams).toBe("10000");
    expect(coverage.uncoveredBands).toEqual([
      {
        fromValue: "500.00",
        toValue: "5000.00",
        acceptedWeightGrams: "1000",
        upToWeightGrams: "10000",
      },
    ]);
  });

  it("reports no gap when one tier is at least as wide as every other in both directions", () => {
    const coverage = analyseCoverage([
      tier({ id: "wide", region: "DE", valueCeiling: "9000.00", weightGrams: "9000" }),
      tier({ id: "narrow", region: "DE", valueCeiling: "500.00", weightGrams: "1000" }),
    ]);

    expect(coverage.uncoveredBands).toEqual([]);
  });

  it("merges neighbouring bands that accept the same weight", () => {
    const coverage = analyseCoverage([
      tier({ id: "a", region: "DE", valueCeiling: "100.00", weightGrams: "9000" }),
      tier({ id: "b", region: "DE", valueCeiling: "200.00", weightGrams: "1000" }),
      tier({ id: "c", region: "DE", valueCeiling: "300.00", weightGrams: "1000" }),
    ]);

    expect(coverage.uncoveredBands).toEqual([
      {
        fromValue: "100.00",
        toValue: "300.00",
        acceptedWeightGrams: "1000",
        upToWeightGrams: "9000",
      },
    ]);
  });
});
