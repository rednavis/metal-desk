import { useQuery } from "@tanstack/react-query";
import { Navigate, Outlet, useLocation } from "react-router";
import { customerViewSchema } from "../../api/types";
import { useApi } from "../../api/useApi";
import { useSignedIn } from "../../api/useSignedIn";
import { usePreferences } from "../../preferences/usePreferences";
import { ErrorState, Spinner } from "../../ui";
import { ME_KEY } from "./meKey";

/**
 * The route guard. A protected screen renders **nothing** of its own until the server has said who
 * the token belongs to: with no token the customer is sent to sign-in, with the page they wanted in
 * `from`; with a token, a spinner shows until `/auth/me` answers, so a slow connection never shows a
 * protected page that then turns out to be off limits. An expired token answers 401, which the API
 * client turns into the same redirect to sign-in.
 */
export function RequireAuth() {
  const { t } = usePreferences();
  const client = useApi();
  const signedIn = useSignedIn();
  const location = useLocation();
  const me = useQuery({
    queryKey: ME_KEY,
    queryFn: () => client.get("/auth/me", { schema: customerViewSchema }),
    enabled: signedIn,
    retry: false,
  });

  if (!signedIn) {
    const from = location.pathname + location.search;
    return <Navigate to={`/sign-in?from=${encodeURIComponent(from)}`} replace />;
  }
  if (me.isError) return <ErrorState error={me.error} />;
  if (!me.isSuccess) return <Spinner label={t("spinner.loading")} />;
  return <Outlet />;
}
