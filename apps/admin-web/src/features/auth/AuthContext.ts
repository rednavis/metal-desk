import { createContext } from "react";

export interface AuthState {
  signedIn: boolean;
  /** Signs in; rejects with an `ApiError` (401 for any bad credential, 429 when locked out). */
  signIn: (login: string, password: string) => Promise<void>;
  /** Forgets the token and everything the signed-in user could see. */
  signOut: () => Promise<void>;
}

export const AuthContext = createContext<AuthState | null>(null);
