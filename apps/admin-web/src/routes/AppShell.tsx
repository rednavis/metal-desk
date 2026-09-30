import { NavLink, Outlet } from "react-router";
import { Layout } from "../ui";

/** The frame every page of the back office sits in. There is no sign-in link: the proxy owns that. */
export function AppShell() {
  return (
    <Layout
      title="MetalDesk Admin"
      nav={
        <>
          <NavLink to="/">Overview</NavLink>
          <NavLink to="/tiers">Delivery tiers</NavLink>
          <NavLink to="/quotes">Manager quotes</NavLink>
          <NavLink to="/orders">Orders</NavLink>
        </>
      }
    >
      <Outlet />
    </Layout>
  );
}
