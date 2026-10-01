import { NavLink, Outlet } from "react-router";
import { useNavigate } from "react-router";
import { useAuth } from "../features/auth/useAuth";
import { useStaff } from "../features/staff/useStaff";
import { Button, Layout } from "../ui";

/**
 * The frame every page of the back office sits in. The header says who the server sees the staff
 * member as, so they know whose name their actions are recorded under, and offers sign-out.
 */
export function AppShell() {
  const staff = useStaff();
  const { signOut } = useAuth();
  const navigate = useNavigate();
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
        <>
          <span data-testid="staff-identity">
            {staff.data
              ? `Signed in as ${staff.data.login} (${staff.data.role})`
              : "Identity unavailable"}
          </span>{" "}
          <Button
            variant="secondary"
            onClick={() => {
              void signOut().then(() => navigate("/login", { replace: true }));
            }}
          >
            Sign out
          </Button>
        </>
      }
    >
      <Outlet />
    </Layout>
  );
}
