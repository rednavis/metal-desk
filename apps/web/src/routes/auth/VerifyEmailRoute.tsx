import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router";
import { isApiError } from "../../api/errors";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, ErrorState, Field } from "../../ui";

/**
 * Confirming an email address with the emailed code (BRD FR-2.3, FR-2.6). The server gives one
 * answer for a wrong, an expired, a used-up and an unknown code, so this screen has one message for
 * all of them and cannot be used to tell them apart.
 */
export function VerifyEmailRoute() {
  const { t } = usePreferences();
  const client = useApi();
  const [params] = useSearchParams();
  const [reference, setReference] = useState(params.get("reference") ?? "");
  const [code, setCode] = useState(params.get("code") ?? "");
  const verify = useMutation({
    mutationFn: () =>
      client.post("/account/verify-email", {
        body: { reference: reference.trim(), code: code.trim() },
      }),
  });

  if (verify.isSuccess) {
    return (
      <>
        <h1>{t("auth.verify.title")}</h1>
        <p role="status">{t("auth.verify.done")}</p>
        <Link to="/sign-in">{t("auth.signIn.submit")}</Link>
      </>
    );
  }
  const rejected = isApiError(verify.error) && verify.error.status === 400;
  return (
    <>
      <h1>{t("auth.verify.title")}</h1>
      <form
        noValidate
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          verify.mutate();
        }}
      >
        {rejected ? <p role="alert">{t("auth.verify.failed")}</p> : null}
        {verify.isError && !rejected ? <ErrorState error={verify.error} /> : null}
        <Field
          label={t("auth.field.reference")}
          name="reference"
          value={reference}
          onChange={(event) => {
            setReference(event.target.value);
          }}
        />
        <Field
          label={t("auth.field.code")}
          name="code"
          autoComplete="one-time-code"
          value={code}
          onChange={(event) => {
            setCode(event.target.value);
          }}
        />
        <Button type="submit" disabled={verify.isPending}>
          {t("auth.verify.submit")}
        </Button>
      </form>
    </>
  );
}
