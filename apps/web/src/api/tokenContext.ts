import { createContext } from "react";
import type { TokenStore } from "./tokenStore";

/** The token store of the running app; see {@link useSignedIn}. */
export const TokenStoreContext = createContext<TokenStore | null>(null);
