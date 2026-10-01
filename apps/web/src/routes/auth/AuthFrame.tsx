import { Outlet } from "react-router";

/** The frame of the account screens (sign-in, registration, password reset): one centred card. */
export function AuthFrame() {
  return (
    <div className="md-auth">
      <Outlet />
    </div>
  );
}
