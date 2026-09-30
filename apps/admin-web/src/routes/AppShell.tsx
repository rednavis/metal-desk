import { NavLink, Outlet } from "react-router";
import { useStaff } from "../features/staff/useStaff";
import { Layout } from "../ui";

/**
 * The frame every page of the back office sits in. There is no link to establish an identity: the proxy owns that.
 * The header says who the server sees the staff member as, so they know whose name their actions
 * are recorded under.
 */
export function AppShell() {
  const staff = useStaff();
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
      actions={
        <span data-testid="staff-identity">
          {staff.data ? `Acting as ${staff.data.email}` : "Identity unavailable"}
        </span>
      }
    >
      <Outlet />
    </Layout>
  );
}
