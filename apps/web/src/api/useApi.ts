import { useContext } from "react";
import type { ApiClient } from "./client";
import { ApiContext } from "./apiContext";

/** The app's API client. Must be used under {@link ApiContext.Provider}. */
export function useApi(): ApiClient {
  const client = useContext(ApiContext);
  if (client === null) throw new Error("useApi must be used inside an ApiContext provider");
  return client;
}
