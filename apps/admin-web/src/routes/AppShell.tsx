import { NavLink, Outlet, useNavigate } from "react-router";
import { useAuth } from "../features/auth/useAuth";
import { useStaff } from "../features/staff/useStaff";
import { BoxIcon, Button, Layout, OverviewIcon, QuoteIcon, TruckIcon } from "../ui";

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
          <NavLink to="/" end>
            <OverviewIcon />
            Overview
          </NavLink>
          <NavLink to="/quotes">
            <QuoteIcon />
            Manager quotes
          </NavLink>
          <NavLink to="/orders">
            <BoxIcon />
            Orders
          </NavLink>
          <NavLink to="/tiers">
            <TruckIcon />
            Delivery tiers
          </NavLink>
        </>
      }
      actions={
        <>
          <span className="md-identity">
            {staff.data ? (
              <span className="md-avatar" aria-hidden="true">
                {staff.data.login.slice(0, 1)}
              </span>
            ) : null}
            <span data-testid="staff-identity">
              {staff.data
                ? `Signed in as ${staff.data.login} (${staff.data.role})`
                : "Identity unavailable"}
            </span>
          </span>
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
