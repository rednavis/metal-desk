import { createContext } from "react";

/** An account this browser has signed in to in this tab, which the customer can switch back to. */
export interface KnownAccount {
  customerId: string;
  /** What the customer typed to sign in; shown in the switcher. Held in memory only. */
  label: string;
}

export interface AuthState {
  signedIn: boolean;
  /** The signed-in account's id, once known. */
  currentId: string | undefined;
  /** Other accounts signed in to earlier in this tab. */
  otherAccounts: KnownAccount[];
  /** Rejects with the `ApiError` of a failed attempt. */
  signIn: (identifier: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  switchTo: (customerId: string) => Promise<void>;
}

export const AuthContext = createContext<AuthState | null>(null);
