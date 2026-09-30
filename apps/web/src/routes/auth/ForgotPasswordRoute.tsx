import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, ErrorState, Field } from "../../ui";

/**
 * Requesting a password reset (BRD FR-2.4). The server answers every address with the same 202, and
 * so does this screen: one fixed confirmation, written here rather than taken from the response, so
 * there is no state in which an address is reported as unknown.
 */
export function ForgotPasswordRoute() {
  const { t } = usePreferences();
  const client = useApi();
  const [email, setEmail] = useState("");
  const request = useMutation({
    mutationFn: () =>
      client.post("/account/password-reset/request", { body: { email: email.trim() } }),
  });

  return (
    <>
      <h1>{t("auth.forgot.title")}</h1>
      {request.isSuccess ? (
        <p role="status">{t("auth.forgot.sent")}</p>
      ) : (
        <form
          noValidate
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            request.mutate();
          }}
        >
          {request.isError ? <ErrorState error={request.error} /> : null}
          <Field
            label={t("auth.field.email")}
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value);
            }}
          />
          <Button type="submit" disabled={request.isPending}>
            {t("auth.forgot.submit")}
          </Button>
        </form>
      )}
    </>
  );
}
