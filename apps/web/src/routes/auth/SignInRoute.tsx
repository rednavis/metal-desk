import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router";
import { isApiError } from "../../api/errors";
import { safeDestination } from "../../features/auth/destination";
import { useAuth } from "../../features/auth/useAuth";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, ErrorState, Field } from "../../ui";

type Failure = "rejected" | "throttled" | { other: unknown };

/**
 * Sign in with an email address or a phone number (BRD FR-2.1, FR-2.2).
 *
 * **Every failure reads the same.** An unknown identifier, a wrong password and a disabled account
 * all come back from the server as one 401, and this screen shows one fixed sentence for it: never
 * the server's text, never which field was wrong, and no suggestion to register. Throttling (429) is
 * the single exception, because the customer must know to wait; it says so without saying anything
 * about whether the account exists, since the server throttles by the caller's address and before it
 * looks at the identifier. Nothing is checked before the form is submitted.
 */
export function SignInRoute() {
  const { t } = usePreferences();
  const { signedIn, signIn } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const destination = safeDestination(params.get("from"));
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [failure, setFailure] = useState<Failure | undefined>();
  const [busy, setBusy] = useState(false);

  if (signedIn && !busy) return <Navigate to={destination} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setFailure(undefined);
    try {
      await signIn(identifier.trim(), password);
      void navigate(destination, { replace: true });
    } catch (error) {
      setBusy(false);
      if (isApiError(error) && error.status === 401) setFailure("rejected");
      else if (isApiError(error) && error.status === 429) setFailure("throttled");
      else setFailure({ other: error });
    }
  }

  return (
    <>
      <h1>{t("page.signIn.title")}</h1>
      <form
        onSubmit={(event) => {
          void submit(event);
        }}
        noValidate
      >
        {failure === "rejected" ? <p role="alert">{t("auth.signIn.failed")}</p> : null}
        {failure === "throttled" ? <p role="alert">{t("auth.signIn.throttled")}</p> : null}
        {typeof failure === "object" ? <ErrorState error={failure.other} /> : null}
        <Field
          label={t("auth.field.identifier")}
          name="identifier"
          autoComplete="username"
          value={identifier}
          onChange={(event) => {
            setIdentifier(event.target.value);
          }}
        />
        <Field
          label={t("auth.field.password")}
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => {
            setPassword(event.target.value);
          }}
        />
        <Button type="submit" disabled={busy}>
          {t("auth.signIn.submit")}
        </Button>
      </form>
      <p>
        <Link to="/forgot-password">{t("auth.signIn.forgot")}</Link>
      </p>
      <p>
        <Link to="/register">{t("auth.signIn.register")}</Link>
      </p>
    </>
  );
}
