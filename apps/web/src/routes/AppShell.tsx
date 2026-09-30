import { NavLink, Outlet } from "react-router";
import { AccountMenu } from "../features/auth/AccountMenu";
import { AuthProvider } from "../features/auth/AuthProvider";
import { CartBadge } from "../features/cart/CartBadge";
import { CartProvider } from "../features/cart/CartProvider";
import { SearchBox } from "../features/catalog/SearchBox";
import { usePreferences } from "../preferences/usePreferences";
import { Layout, PreferenceSwitcher } from "../ui";

/** The frame every page of the storefront sits in; it also holds the cart, which every page can change. */
export function AppShell() {
  const { t } = usePreferences();
  return (
    <AuthProvider>
      <CartProvider>
        <Layout
          title={t("app.title")}
          actions={
            <>
              <SearchBox />
              <PreferenceSwitcher />
            </>
          }
          nav={
            <>
              <NavLink to="/">{t("nav.home")}</NavLink>
              <NavLink to="/catalog">{t("nav.catalog")}</NavLink>
              <NavLink to="/cart">
                <CartBadge />
              </NavLink>
              <NavLink to="/inquiry">{t("nav.contact")}</NavLink>
              <AccountMenu />
            </>
          }
        >
          <Outlet />
        </Layout>
      </CartProvider>
    </AuthProvider>
  );
}
