import { useContext } from "react";
import { AuthContext, type AuthState } from "./AuthContext";

/** Signing in and out. Must be used inside an `AuthProvider`. */
export function useAuth(): AuthState {
  const auth = useContext(AuthContext);
  if (auth === null) throw new Error("useAuth must be used inside an AuthProvider");
  return auth;
}
