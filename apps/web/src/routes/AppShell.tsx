import { NavLink, Outlet } from "react-router";
import { usePreferences } from "../preferences/usePreferences";
import { Layout, PreferenceSwitcher } from "../ui";

/** The frame every page of the storefront sits in. */
export function AppShell() {
  const { t } = usePreferences();
  return (
    <Layout
      title={t("app.title")}
      actions={<PreferenceSwitcher />}
      nav={
        <>
          <NavLink to="/">{t("nav.home")}</NavLink>
          <NavLink to="/catalog">{t("nav.catalog")}</NavLink>
          <NavLink to="/cart">{t("nav.cart")}</NavLink>
          <NavLink to="/sign-in">{t("nav.signIn")}</NavLink>
        </>
      }
    >
      <Outlet />
    </Layout>
  );
}
