import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { isApiError } from "../../api/errors";
import { registrationAcceptedSchema } from "../../api/types";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, ErrorState, Field } from "../../ui";

/**
 * Registration (BRD FR-2.3): the minimum profile, then an explanation of what happens next.
 *
 * Nothing here asks whether the address is already registered, before or after submitting: the
 * server answers a new and an existing address identically (202), so the screen can say only that a
 * verification email is on its way and that checkout needs the address verified.
 */
export function RegisterRoute() {
  const { t } = usePreferences();
  const client = useApi();
  const [form, setForm] = useState({ name: "", email: "", phone: "", password: "" });
  const register = useMutation({
    mutationFn: () =>
      client.post("/account/register", {
        body: {
          ...form,
          name: form.name.trim(),
          email: form.email.trim(),
          phone: form.phone.trim(),
        },
        schema: registrationAcceptedSchema,
      }),
  });

  if (register.isSuccess) {
    return (
      <section aria-labelledby="registered-title">
        <h1 id="registered-title">{t("auth.register.sent.title")}</h1>
        <p>{t("auth.register.sent.body", { email: form.email.trim() })}</p>
        <Link
          to={{
            pathname: "/verify-email",
            search: `?reference=${encodeURIComponent(register.data.reference)}`,
          }}
        >
          {t("auth.register.enterCode")}
        </Link>
      </section>
    );
  }

  const violations = isApiError(register.error) ? register.error.violations : [];
  const message = (field: string) => violations.find((v) => v.field === field)?.message;
  const set = (field: keyof typeof form) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };
  const field = (name: keyof typeof form, label: string, type = "text", autoComplete?: string) => (
    <Field
      label={label}
      name={name}
      type={type}
      autoComplete={autoComplete}
      value={form[name]}
      error={message(name)}
      onChange={(event) => {
        set(name)(event.target.value);
      }}
    />
  );
  return (
    <>
      <h1>{t("auth.register.title")}</h1>
      <form
        noValidate
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          register.mutate();
        }}
      >
        {register.isError && violations.length === 0 ? <ErrorState error={register.error} /> : null}
        {field("name", t("auth.field.name"), "text", "name")}
        {field("email", t("auth.field.email"), "email", "email")}
        {field("phone", t("auth.field.phone"), "tel", "tel")}
        {field("password", t("auth.field.password"), "password", "new-password")}
        <Button type="submit" disabled={register.isPending}>
          {t("auth.register.submit")}
        </Button>
      </form>
    </>
  );
}
