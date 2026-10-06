import { createContext } from "react";
import type { ApiClient } from "./client";

/** The API client of the running app; see {@link useApi}. */
export const ApiContext = createContext<ApiClient | null>(null);
