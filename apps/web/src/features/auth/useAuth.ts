import { useContext } from "react";
import { AuthContext, type AuthState } from "./AuthContext";

/** Sign-in, sign-out and account switching. Must be used under {@link AuthProvider}. */
export function useAuth(): AuthState {
  const state = useContext(AuthContext);
  if (state === null) throw new Error("useAuth must be used inside an AuthProvider");
  return state;
}
