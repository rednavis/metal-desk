import { NavLink, Outlet } from "react-router";
import { Layout } from "../ui";

/** The frame every page of the storefront sits in. */
export function AppShell() {
  return (
    <Layout
      title="MetalDesk"
      nav={
        <>
          <NavLink to="/">Home</NavLink>
          <NavLink to="/catalog">Catalog</NavLink>
          <NavLink to="/cart">Cart</NavLink>
          <NavLink to="/sign-in">Sign in</NavLink>
        </>
      }
    >
      <Outlet />
    </Layout>
  );
}
