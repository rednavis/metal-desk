import { useState, type FormEvent } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router";
import { isApiError } from "../../api/errors";
import { safeDestination } from "../../features/auth/destination";
import { useAuth } from "../../features/auth/useAuth";
import { Button, ErrorState, Field } from "../../ui";

type Failure = "rejected" | "throttled" | { other: unknown };

/**
 * The back-office login form.
 *
 * **Every failure reads the same.** An unknown login, a wrong password and a disabled user all come
 * back from the server as one 401, and this screen shows one fixed sentence for it: never the
 * server's text and never which field was wrong. Throttling (429) is the one exception, because the
 * user must know to wait. Nothing is checked before the form is submitted.
 */
export function LoginRoute() {
  const { signedIn, signIn } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const destination = safeDestination(params.get("from"));
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [failure, setFailure] = useState<Failure | undefined>();
  const [busy, setBusy] = useState(false);

  if (signedIn && !busy) return <Navigate to={destination} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setFailure(undefined);
    try {
      await signIn(login.trim(), password);
      void navigate(destination, { replace: true });
    } catch (error) {
      setBusy(false);
      if (isApiError(error) && error.status === 401) setFailure("rejected");
      else if (isApiError(error) && error.status === 429) setFailure("throttled");
      else setFailure({ other: error });
    }
  }

  return (
    <main className="md-login">
      <h1>MetalDesk Admin</h1>
      <h2>Sign in</h2>
      <form
        onSubmit={(event) => {
          void submit(event);
        }}
        noValidate
      >
        {failure === "rejected" ? <p role="alert">Invalid login or password.</p> : null}
        {failure === "throttled" ? (
          <p role="alert">Too many attempts. Wait a few minutes and try again.</p>
        ) : null}
        {typeof failure === "object" ? <ErrorState error={failure.other} /> : null}
        <Field
          label="Login"
          name="login"
          autoComplete="username"
          value={login}
          onChange={(event) => {
            setLogin(event.target.value);
          }}
        />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => {
            setPassword(event.target.value);
          }}
        />
        <Button type="submit" disabled={busy}>
          Sign in
        </Button>
      </form>
    </main>
  );
}
