import { Navigate, Outlet, useLocation } from "react-router";
import { useSignedIn } from "../../api/useSignedIn";
import { ErrorState, Spinner } from "../../ui";
import { useStaff } from "../staff/useStaff";

/**
 * The route guard. A protected screen renders **nothing** of its own until the server has said who
 * the token belongs to: with no token the user is sent to the login form, with the page they wanted
 * in `from`; with a token, a spinner shows until `/admin/me` answers. An expired or revoked token
 * answers 401, which the API client turns into the same redirect.
 */
export function RequireAuth() {
  const signedIn = useSignedIn();
  const location = useLocation();
  const staff = useStaff(signedIn);

  if (!signedIn) {
    const from = location.pathname + location.search;
    return <Navigate to={`/login?from=${encodeURIComponent(from)}`} replace />;
  }
  if (staff.isError) return <ErrorState error={staff.error} />;
  if (!staff.isSuccess) return <Spinner label="Loading" />;
  return <Outlet />;
}
