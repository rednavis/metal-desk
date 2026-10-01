import { useState } from "react";
import { Link } from "react-router";
import type { TierView, TierWarning } from "../../api/types";
import { useTierMutations, useTiers } from "../../features/tiers/useTiers";
import { Button, ConfirmDialog, EmptyState, ErrorState, Spinner } from "../../ui";
import { TierCoverageView } from "./TierCoverageView";
import { WarningList } from "./WarningList";

/**
 * The delivery tiers, grouped by region, each region with its coverage (BRD FR-5.1). Deleting a tier
 * asks first; deleting the last one of a region puts the warning in the dialog and needs it ticked,
 * because it routes every order there to manual pricing. What the server says about the result is
 * shown as it says it.
 */
export function TierListRoute() {
  const tiers = useTiers();
  const { remove } = useTierMutations();
  const [removing, setRemoving] = useState<TierView | undefined>();
  const [warnings, setWarnings] = useState<TierWarning[]>([]);

  if (tiers.isPending) return <Spinner />;
  if (tiers.isError) return <ErrorState error={tiers.error} />;

  const byRegion = new Map<string, TierView[]>();
  for (const tier of tiers.data)
    byRegion.set(tier.region, [...(byRegion.get(tier.region) ?? []), tier]);
  const regions = [...byRegion.keys()].sort();
  const lastOfRegion = removing !== undefined && (byRegion.get(removing.region)?.length ?? 0) === 1;

  return (
    <>
      <div className="md-page-header">
        <h1>Delivery tiers</h1>
        <Link to="/tiers/new" className="md-button">
          New tier
        </Link>
      </div>
      <WarningList warnings={warnings} />
      {remove.isError ? <ErrorState error={remove.error} /> : null}
      {regions.length === 0 ? (
        <EmptyState
          title="No tiers configured"
          description="Without a tier for a region, every order to it goes to manager handoff."
          action={<Link to="/tiers/new">Add the first tier</Link>}
        />
      ) : null}
      {regions.map((region) => {
        const inRegion = byRegion.get(region) ?? [];
        return (
          <section key={region} aria-labelledby={`region-${region}`}>
            <h2 id={`region-${region}`}>{region}</h2>
            <table className="md-table">
              <thead>
                <tr>
                  <th scope="col">Tier</th>
                  <th scope="col">Value ceiling</th>
                  <th scope="col">Weight ceiling</th>
                  <th scope="col">Delivery price</th>
                  <th scope="col">Transit</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {inRegion.map((tier) => (
                  <tr key={tier.id} data-testid="tier-row">
                    <th scope="row">{tier.id}</th>
                    <td>{`${tier.valueCeiling} ${tier.currency}`}</td>
                    <td>{`${tier.weightGrams} g`}</td>
                    <td>{`${tier.deliveryPrice} ${tier.currency}`}</td>
                    <td>{`${String(tier.minDays)} to ${String(tier.maxDays)} days`}</td>
                    <td className="md-actions">
                      <Link to={`/tiers/${encodeURIComponent(tier.id)}`}>{`Edit ${tier.id}`}</Link>
                      <Button
                        variant="secondary"
                        aria-label={`Delete ${tier.id}`}
                        onClick={() => {
                          setRemoving(tier);
                        }}
                      >
                        Delete
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TierCoverageView region={region} tiers={inRegion} />
          </section>
        );
      })}
      {removing ? (
        <ConfirmDialog
          title={`Delete tier ${removing.id}?`}
          confirmLabel="Delete tier"
          busy={remove.isPending}
          acknowledgement={
            lastOfRegion
              ? `I understand that every order to ${removing.region} will go to manager handoff.`
              : undefined
          }
          onCancel={() => {
            setRemoving(undefined);
          }}
          onConfirm={() => {
            remove.mutate(removing.id, {
              onSuccess: (result) => {
                setWarnings(result.warnings);
                setRemoving(undefined);
              },
              onError: () => {
                setRemoving(undefined);
              },
            });
          }}
        >
          {lastOfRegion ? (
            <p role="alert" className="md-notice md-notice--warning">
              {`This is the last tier of ${removing.region}. Without it no order to ${removing.region} can be priced automatically: every one goes to manager handoff and needs a manager's price.`}
            </p>
          ) : (
            <p>{`Orders to ${removing.region} will no longer be offered this tier; the remaining tiers apply.`}</p>
          )}
        </ConfirmDialog>
      ) : null}
    </>
  );
}
