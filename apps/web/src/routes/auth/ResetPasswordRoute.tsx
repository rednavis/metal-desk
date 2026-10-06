import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router";
import { isApiError } from "../../api/errors";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, ErrorState, Field } from "../../ui";

/**
 * Completing a reset from the emailed link, `/reset-password?reference=...&code=...` (BRD FR-2.4).
 * An unusable link (expired, used, wrong) is one message with a way to ask for another; a refused
 * new password is shown as the server words it, because that is something the customer can fix.
 */
export function ResetPasswordRoute() {
  const { t } = usePreferences();
  const client = useApi();
  const [params] = useSearchParams();
  const reference = params.get("reference") ?? "";
  const code = params.get("code") ?? "";
  const [newPassword, setNewPassword] = useState("");
  const reset = useMutation({
    mutationFn: () =>
      client.post("/account/password-reset/confirm", { body: { reference, code, newPassword } }),
  });

  if (reset.isSuccess) {
    return (
      <>
        <h1>{t("auth.reset.title")}</h1>
        <p role="status">{t("auth.reset.done")}</p>
        <Link to="/sign-in">{t("auth.signIn.submit")}</Link>
      </>
    );
  }
  const error = reset.error;
  const linkUnusable = isApiError(error) && error.code === "verification.invalid";
  return (
    <>
      <h1>{t("auth.reset.title")}</h1>
      <form
        noValidate
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          reset.mutate();
        }}
      >
        {linkUnusable ? (
          <p role="alert">
            {t("auth.reset.failed")} <Link to="/forgot-password">{t("auth.reset.request")}</Link>
          </p>
        ) : null}
        {reset.isError && !linkUnusable ? <ErrorState error={error} /> : null}
        <Field
          label={t("auth.field.newPassword")}
          name="newPassword"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(event) => {
            setNewPassword(event.target.value);
          }}
        />
        <Button type="submit" disabled={reset.isPending}>
          {t("auth.reset.submit")}
        </Button>
      </form>
    </>
  );
}
