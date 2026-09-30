import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router";
import { isApiError } from "../../api/errors";
import type { TierRequest, TierResult, TierView } from "../../api/types";
import { fieldsFor, type FieldName } from "../../features/tiers/fieldErrors";
import { useTier, useTierMutations, useTiers } from "../../features/tiers/useTiers";
import { Button, ErrorState, Field, SelectField, Spinner } from "../../ui";
import { TierCoverageView } from "./TierCoverageView";
import { WarningList } from "./WarningList";

const UNITS = [
  { value: "GRAM", label: "Grams" },
  { value: "KILOGRAM", label: "Kilograms" },
  { value: "TROY_OUNCE", label: "Troy ounces" },
] as const;

interface FormState {
  region: string;
  valueCeiling: string;
  weightCeiling: string;
  weightUnit: TierRequest["weightUnit"];
  currency: string;
  deliveryPrice: string;
  minDays: string;
  maxDays: string;
}

const EMPTY: FormState = {
  region: "",
  valueCeiling: "",
  weightCeiling: "",
  weightUnit: "KILOGRAM",
  currency: "EUR",
  deliveryPrice: "",
  minDays: "",
  maxDays: "",
};

function fromTier(tier: TierView): FormState {
  return {
    region: tier.region,
    valueCeiling: tier.valueCeiling,
    weightCeiling: tier.weightGrams,
    weightUnit: "GRAM",
    currency: tier.currency,
    deliveryPrice: tier.deliveryPrice,
    minDays: String(tier.minDays),
    maxDays: String(tier.maxDays),
  };
}

/** Creates a tier at `/tiers/new` or changes one at `/tiers/:tierId`. */
export function TierEditRoute() {
  const { tierId } = useParams();
  const existing = useTier(tierId);
  if (tierId !== undefined) {
    if (existing.isPending) return <Spinner />;
    if (existing.isError) return <ErrorState error={existing.error} />;
  }
  return <TierForm key={tierId ?? "new"} id={tierId} initial={existing.data} />;
}

function TierForm({ id, initial }: { id?: string; initial?: TierView }) {
  const { save } = useTierMutations();
  const [form, setForm] = useState<FormState>(initial ? fromTier(initial) : EMPTY);
  const [saved, setSaved] = useState<TierResult | undefined>();
  const region = saved?.tier.region ?? (form.region.trim().toUpperCase() || undefined);
  const sameRegion = useTiers(region, region?.length === 2);

  const refusal = isApiError(save.error) ? save.error : undefined;
  const fields = refusal ? fieldsFor(refusal.code) : [];
  const error = (field: FieldName) => (fields.includes(field) ? refusal?.message : undefined);
  const set = (field: keyof FormState) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };
  const text = (field: FieldName, label: string, inputMode?: "decimal" | "numeric") => (
    <Field
      label={label}
      name={field}
      inputMode={inputMode}
      value={form[field]}
      error={error(field)}
      onChange={(event) => {
        set(field)(event.target.value);
      }}
    />
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate(
      {
        id,
        request: {
          region: form.region.trim(),
          valueCeiling: form.valueCeiling.trim(),
          weightCeiling: form.weightCeiling.trim(),
          weightUnit: form.weightUnit,
          currency: form.currency.trim().toUpperCase(),
          deliveryPrice: form.deliveryPrice.trim(),
          minDays: Number(form.minDays),
          maxDays: Number(form.maxDays),
        },
      },
      { onSuccess: setSaved },
    );
  }

  return (
    <>
      <h1>{id === undefined ? "New delivery tier" : `Edit tier ${id}`}</h1>
      <p>
        <Link to="/tiers">Back to the tiers</Link>
      </p>
      {saved ? (
        <section role="status" className="md-notice md-notice--ok">
          <strong>{`Tier ${saved.tier.id} saved.`}</strong>
        </section>
      ) : null}
      <WarningList warnings={saved?.warnings ?? []} />
      <form className="md-form" onSubmit={submit} noValidate>
        {save.isError && fields.length === 0 ? <ErrorState error={save.error} /> : null}
        {text("region", "Region (two-letter country code)")}
        {text("valueCeiling", "Value ceiling, before tax", "decimal")}
        {text("weightCeiling", "Weight ceiling", "decimal")}
        <SelectField
          label="Weight unit"
          value={form.weightUnit}
          options={UNITS}
          onChange={(value) => {
            set("weightUnit")(value);
          }}
        />
        {text("currency", "Currency")}
        {text("deliveryPrice", "Delivery price, insurance included", "decimal")}
        {text("minDays", "Fewest days in transit", "numeric")}
        {text("maxDays", "Most days in transit", "numeric")}
        <Button type="submit" disabled={save.isPending}>
          Save tier
        </Button>
      </form>
      {region && sameRegion.data ? (
        <TierCoverageView region={region.toUpperCase()} tiers={sameRegion.data} />
      ) : null}
    </>
  );
}
