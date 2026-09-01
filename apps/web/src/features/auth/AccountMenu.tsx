import { NavLink, useLocation, useNavigate } from "react-router";
import { usePreferences } from "../../preferences/usePreferences";
import { Button } from "../../ui";
import { AccountSwitcher } from "./AccountSwitcher";
import { useAuth } from "./useAuth";

/**
 * The account links of the header. Signing out from inside a checkout goes to the resume-or-home
 * prompt (BRD FR-2.7), so the customer is never dropped somewhere with their checkout gone and no
 * explanation; from anywhere else it goes home.
 */
export function AccountMenu() {
  const { t } = usePreferences();
  const { signedIn, signOut } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  if (!signedIn) {
    return (
      <>
        <NavLink to="/sign-in">{t("nav.signIn")}</NavLink>
        <NavLink to="/register">{t("nav.register")}</NavLink>
      </>
    );
  }
  return (
    <>
      <NavLink to="/orders">{t("nav.orders")}</NavLink>
      <AccountSwitcher />
      <Button
        variant="secondary"
        onClick={() => {
          const midCheckout = location.pathname.startsWith("/checkout");
          void signOut().then(() =>
            navigate(midCheckout ? "/signed-out" : "/", { state: { midCheckout } }),
          );
        }}
      >
        {t("nav.signOut")}
      </Button>
    </>
  );
}
