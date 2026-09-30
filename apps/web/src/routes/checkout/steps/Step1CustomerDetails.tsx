import { useQuery } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { isApiError } from "../../../api/errors";
import { addressViewSchema, type DetailsView, type FieldViolation } from "../../../api/types";
import { useApi } from "../../../api/useApi";
import { useSignedIn } from "../../../api/useSignedIn";
import { policyVersion } from "../../../config";
import { usePreferences } from "../../../preferences/usePreferences";
import { Button, ErrorState, Field, Spinner } from "../../../ui";
import type { CheckoutApi } from "../useCheckoutSession";

interface FormState {
  name: string;
  email: string;
  phone: string;
  street: string;
  city: string;
  country: string;
  postalCode: string;
  companyName: string;
  companyAddress: string;
  note: string;
  rememberMe: boolean;
  password: string;
}

const EMPTY: FormState = {
  name: "",
  email: "",
  phone: "",
  street: "",
  city: "",
  country: "",
  postalCode: "",
  companyName: "",
  companyAddress: "",
  note: "",
  rememberMe: false,
  password: "",
};

function fromDetails(details: DetailsView): FormState {
  return {
    ...EMPTY,
    name: details.name,
    email: details.email,
    phone: details.phone,
    street: details.street,
    city: details.city,
    country: details.country,
    postalCode: details.postalCode,
    companyName: details.companyName ?? "",
    companyAddress: details.companyAddress ?? "",
    note: details.note ?? "",
  };
}

/**
 * Step 1 (BRD FR-4.1 to FR-4.3, FR-3.5): the customer and delivery details.
 *
 * - A signed-in customer's saved delivery address pre-fills the form; every field stays editable and
 *   what is sent is always the full set as it stands in the form.
 * - Validation is the server's: each violation it returns is shown beside its own input, all of them
 *   at once. The one check made here is that the privacy policy is accepted, because nothing can be
 *   sent without it.
 * - Privacy acceptance is its own checkbox, unchecked by default, with its own label.
 * - "Remember me" asks for a password and converts the checkout into an account; the checkout goes
 *   on straight away and does not wait for the verification email.
 */
export function Step1CustomerDetails({
  checkout,
  onDone,
}: {
  checkout: CheckoutApi;
  onDone: () => void;
}) {
  const { t } = usePreferences();
  const client = useApi();
  const signedIn = useSignedIn();
  const session = checkout.session.data;
  const existing = session?.details;
  const profile = useQuery({
    queryKey: ["cart", "delivery-profile"],
    queryFn: () => client.get("/cart/delivery-profile", { schema: addressViewSchema.optional() }),
    enabled: signedIn && existing === undefined,
    retry: false,
  });

  if (signedIn && existing === undefined && profile.isLoading) {
    return <Spinner label={t("checkout.loading")} />;
  }
  const saved = existing === undefined ? profile.data : undefined;
  const initial = existing
    ? fromDetails(existing)
    : saved
      ? {
          ...EMPTY,
          street: saved.street,
          city: saved.city,
          country: saved.country,
          postalCode: saved.postalCode,
          companyName: saved.companyName ?? "",
          companyAddress: saved.companyAddress ?? "",
        }
      : EMPTY;
  return (
    <Step1Form
      checkout={checkout}
      initial={initial}
      prefilled={saved !== undefined}
      signedIn={signedIn}
      onDone={onDone}
    />
  );
}

function Step1Form({
  checkout,
  initial,
  prefilled,
  signedIn,
  onDone,
}: {
  checkout: CheckoutApi;
  initial: FormState;
  prefilled: boolean;
  signedIn: boolean;
  onDone: () => void;
}) {
  const { t } = usePreferences();
  const [form, setForm] = useState<FormState>(initial);
  const [privacy, setPrivacy] = useState(false);
  const [privacyMissing, setPrivacyMissing] = useState(false);
  const { submitStep1 } = checkout;

  const violations: readonly FieldViolation[] = isApiError(submitStep1.error)
    ? submitStep1.error.violations
    : [];
  const message = (field: string) => violations.find((v) => v.field === field)?.message;
  const set = (field: keyof FormState) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };
  const text = (field: keyof FormState, label: string, extra?: { type?: string }) => (
    <Field
      label={label}
      name={field}
      type={extra?.type ?? "text"}
      value={String(form[field])}
      error={message(fieldKey(field))}
      onChange={(event) => {
        set(field)(event.target.value);
      }}
    />
  );

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!privacy) {
      setPrivacyMissing(true);
      return;
    }
    setPrivacyMissing(false);
    try {
      await submitStep1.mutateAsync({
        name: form.name.trim(),
        contact: { email: form.email.trim(), phone: form.phone.trim() },
        street: form.street.trim(),
        city: form.city.trim(),
        country: form.country.trim().toUpperCase(),
        postalCode: form.postalCode.trim(),
        company: { name: form.companyName.trim(), address: form.companyAddress.trim() },
        note: form.note.trim(),
        privacyPolicyAccepted: true,
        policyVersion,
        account: {
          rememberMe: form.rememberMe,
          password: form.rememberMe ? form.password : undefined,
        },
      });
      onDone();
    } catch {
      // Shown below: field violations beside their inputs, anything else as an error.
    }
  }

  const other = submitStep1.error && violations.length === 0 ? submitStep1.error : null;
  const privacyError = privacyMissing
    ? t("checkout.consent.required")
    : message("privacyPolicyAccepted");
  return (
    <form
      onSubmit={(event) => {
        void submit(event);
      }}
      noValidate
      aria-labelledby="step1-title"
    >
      <h2 id="step1-title">{t("checkout.step1.title")}</h2>
      {prefilled ? <p role="status">{t("checkout.step1.prefilled")}</p> : null}
      {violations.length > 0 ? <p role="alert">{t("checkout.step1.invalid")}</p> : null}
      {other ? <ErrorState error={other} /> : null}
      {text("name", t("checkout.field.name"))}
      {text("email", t("checkout.field.email"), { type: "email" })}
      {text("phone", t("checkout.field.phone"), { type: "tel" })}
      {text("street", t("checkout.field.street"))}
      {text("city", t("checkout.field.city"))}
      {text("country", t("checkout.field.country"))}
      {text("postalCode", t("checkout.field.postalCode"))}
      {text("companyName", t("checkout.field.companyName"))}
      {text("companyAddress", t("checkout.field.companyAddress"))}
      {text("note", t("checkout.field.note"))}

      <div className="md-checkbox">
        <label>
          <input
            type="checkbox"
            name="privacyPolicyAccepted"
            checked={privacy}
            aria-invalid={privacyError ? true : undefined}
            onChange={(event) => {
              setPrivacy(event.target.checked);
              setPrivacyMissing(false);
            }}
          />
          <span>{t("checkout.consent.privacy")}</span>
        </label>
        {privacyError ? <span className="md-field__error">{privacyError}</span> : null}
      </div>

      {signedIn ? null : (
        <div className="md-checkbox">
          <label>
            <input
              type="checkbox"
              name="rememberMe"
              checked={form.rememberMe}
              onChange={(event) => {
                setForm((current) => ({ ...current, rememberMe: event.target.checked }));
              }}
            />
            <span>{t("checkout.rememberMe")}</span>
          </label>
          <p>{t("checkout.rememberMe.note")}</p>
          {form.rememberMe
            ? text("password", t("checkout.field.password"), { type: "password" })
            : null}
        </div>
      )}
      <Button type="submit" disabled={submitStep1.isPending}>
        {t("checkout.continue")}
      </Button>
    </form>
  );
}

/** The server's key for a form field: the nested ones are addressed by path. */
function fieldKey(field: keyof FormState): string {
  switch (field) {
    case "email":
      return "contact.email";
    case "phone":
      return "contact.phone";
    case "companyName":
      return "company.name";
    case "companyAddress":
      return "company.address";
    case "password":
      return "account.password";
    default:
      return field;
  }
}
