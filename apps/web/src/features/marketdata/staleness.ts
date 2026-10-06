import { useEffect, useState } from "react";

/** Why prices are being called stale: the page has not heard from the server, or the server has not heard from the feed. */
export type StaleReason = "fetch" | "feed";

/**
 * Whether the prices on screen can still be presented as live (BRD FR-1.1; the client-side
 * counterpart of the bridge's `/actuator/feed` staleness).
 *
 * Two clocks, because either can freeze a price on screen. If the **last successful fetch** is older
 * than the threshold, the network or the API is down and the panel is showing a memory. If the
 * **newest observation** is older than the threshold, the API answers but the feed behind it has
 * gone quiet, and a customer would be trading against a frozen price even though every fetch
 * succeeds. The second compares the server's timestamp with this device's clock, so a badly wrong
 * device clock can flag fresh prices as stale; the threshold is generous (three intervals) for that
 * reason, and being wrongly cautious is the safe direction.
 *
 * @param fetchedAt when prices were last fetched successfully, in epoch milliseconds; 0 if never
 * @param newestObservedAt when the newest price was observed, in epoch milliseconds; 0 if none
 * @param now the current time, in epoch milliseconds
 * @param afterMs how old is too old
 */
export function assessStaleness(
  fetchedAt: number,
  newestObservedAt: number,
  now: number,
  afterMs: number,
): StaleReason | null {
  if (fetchedAt === 0) return null;
  if (now - fetchedAt > afterMs) return "fetch";
  if (newestObservedAt > 0 && now - newestObservedAt > afterMs) return "feed";
  return null;
}

/** The current time, refreshed every `tickMs`, so staleness appears without anything else happening. */
export function useNow(tickMs: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => {
      setNow(Date.now());
    }, tickMs);
    return () => {
      clearInterval(id);
    };
  }, [tickMs]);
  return now;
}
