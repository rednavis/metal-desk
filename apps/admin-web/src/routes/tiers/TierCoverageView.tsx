import type { TierView } from "../../api/types";
import { analyseCoverage, type TieBreak } from "../../features/tiers/coverage";

const REASON: Record<TieBreak, string> = {
  cheaper: "it is cheaper",
  faster: "it is faster",
  "lower-id": "the prices and transit times are equal, so the lower id wins",
};

/**
 * What a region's tiers cover, written out so the two dangerous conditions are visible (BRD FR-5.1):
 * overlaps, with the tie-break outcome the checkout will produce, and uncovered bands, the orders
 * that go to a manager even though they are under the widest ceilings. A table of tiers shows
 * neither. It is a sorted, annotated list, not a chart.
 */
export function TierCoverageView({
  region,
  tiers,
}: {
  region: string;
  tiers: readonly TierView[];
}) {
  const coverage = analyseCoverage(tiers);
  const unit = coverage.currency ?? "";

  if (coverage.empty) {
    return (
      <section aria-label={`Coverage of ${region}`} className="md-notice md-notice--warning">
        <strong>{`No tier covers ${region}.`}</strong>
        <p>Every order to this region goes to manager handoff.</p>
      </section>
    );
  }
  return (
    <section aria-label={`Coverage of ${region}`}>
      <h3>{`Coverage of ${region}`}</h3>
      <p data-testid="coverage-limits">
        {`Orders worth more than ${coverage.maxValue ?? ""} ${unit} or heavier than ${coverage.maxWeightGrams ?? ""} g go to manager handoff.`}
      </p>

      {coverage.uncoveredBands.length > 0 ? (
        <div className="md-notice md-notice--warning" data-testid="uncovered-bands">
          <strong>Orders within the widest ceilings that no tier accepts:</strong>
          <ul>
            {coverage.uncoveredBands.map((band) => (
              <li key={`${band.fromValue}-${band.toValue}`}>
                {`Worth over ${band.fromValue} up to ${band.toValue} ${unit}: accepted only up to ${band.acceptedWeightGrams} g. Between ${band.acceptedWeightGrams} g and ${band.upToWeightGrams} g they go to manager handoff.`}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p data-testid="no-uncovered-bands">
          No gaps: every order under the widest value and weight is accepted by some tier.
        </p>
      )}

      {coverage.overlaps.length > 0 ? (
        <div data-testid="overlaps">
          <h4>Overlaps</h4>
          <ul>
            {coverage.overlaps.map((overlap) => (
              <li key={`${overlap.winner.id}-${overlap.loser.id}`}>
                {`Tiers ${overlap.winner.id} and ${overlap.loser.id} both accept orders up to ${overlap.upToValue} ${unit} and ${overlap.upToWeightGrams} g. For those, ${overlap.winner.id} is chosen (${REASON[overlap.because]}).`}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}
