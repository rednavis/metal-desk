import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { z } from "zod";
import { useApi } from "../../api/useApi";
import { referencePriceViewSchema } from "../../api/types";
import type { MarketDataConfig } from "../../config";
import { usePreferences } from "../../preferences/usePreferences";
import { assessStaleness, useNow } from "./staleness";

const pricesSchema = z.array(referencePriceViewSchema);

/** Whether the page is in front of the customer. */
export function usePageVisible(): boolean {
  const [visible, setVisible] = useState(() => !document.hidden);
  useEffect(() => {
    const update = () => {
      setVisible(!document.hidden);
    };
    document.addEventListener("visibilitychange", update);
    return () => {
      document.removeEventListener("visibilitychange", update);
    };
  }, []);
  return visible;
}

/** The delay before retry number `failures` (1 for the first): doubling from a base, capped. */
export function backoffDelay(failures: number, baseMs: number, maxMs: number): number {
  return Math.min(baseMs * 2 ** (failures - 1), maxMs);
}

/**
 * The reference prices (BRD FR-1.1), refreshed on a short interval, in the customer's display
 * currency.
 *
 * - **Polling stops while the tab is hidden** and **resumes with an immediate fetch** the moment it
 *   is visible again, so a returning customer never waits out an interval for a fresh price.
 * - **A failed fetch keeps the last values** (the query keeps its data) and the next attempt is
 *   scheduled on a **backoff**, doubling from `retryBaseMs` up to `retryMaxMs`, not at the polling
 *   interval: an outage must not become steady load at full rate. A success returns to the normal
 *   interval.
 * - **Staleness** is reported, see {@link assessStaleness}.
 */
export function useReferencePrices(config: MarketDataConfig) {
  const client = useApi();
  const { currency } = usePreferences();
  const visible = usePageVisible();
  const now = useNow(Math.max(1000, Math.floor(config.staleAfterMs / 4)));

  const query = useQuery({
    queryKey: ["market-data", currency],
    queryFn: () => client.get("/market-data/prices", { schema: pricesSchema }),
    // Timing is ours, below: no library retry, interval or focus refetch to fight with.
    retry: false,
    refetchInterval: false,
    refetchOnWindowFocus: false,
    staleTime: 0,
    // Changing currency refetches, and the old prices stay up (each in its own currency) meanwhile.
    placeholderData: keepPreviousData,
  });
  const { refetch, isFetching } = query;

  // Consecutive failures, for the backoff: one more per failed fetch, forgotten on a success. Kept as
  // state derived while rendering (not in an effect), so the very render that shows a failure already
  // schedules its retry with the right delay.
  const [streak, setStreak] = useState({ dataAt: 0, errorAt: 0, failures: 0 });
  const dataChanged = query.dataUpdatedAt !== streak.dataAt;
  if (dataChanged || query.errorUpdatedAt !== streak.errorAt) {
    setStreak({
      dataAt: query.dataUpdatedAt,
      errorAt: query.errorUpdatedAt,
      failures: dataChanged ? 0 : streak.failures + 1,
    });
  }
  const failures = streak.failures;

  const settledAt = Math.max(query.dataUpdatedAt, query.errorUpdatedAt);
  useEffect(() => {
    if (!visible || isFetching) return;
    const delay =
      failures > 0
        ? backoffDelay(failures, config.retryBaseMs, config.retryMaxMs)
        : config.pollIntervalMs;
    const timer = setTimeout(() => {
      void refetch();
    }, delay);
    return () => {
      clearTimeout(timer);
    };
  }, [
    visible,
    isFetching,
    settledAt,
    failures,
    refetch,
    config.pollIntervalMs,
    config.retryBaseMs,
    config.retryMaxMs,
  ]);

  // Coming back to a hidden tab: fetch now.
  const wasHidden = useRef(false);
  useEffect(() => {
    if (visible && wasHidden.current) void refetch();
    wasHidden.current = !visible;
  }, [visible, refetch]);

  const prices = query.data;
  const newestObservedAt = Math.max(
    0,
    ...(prices ?? []).map((price) => Date.parse(price.observedAt)),
  );
  return {
    prices,
    /** Nothing has loaded yet. */
    isPending: query.isPending,
    /** The latest fetch failed; `prices` may still hold the last good ones. */
    isError: query.isError,
    error: query.error,
    isFetching,
    staleReason: assessStaleness(query.dataUpdatedAt, newestObservedAt, now, config.staleAfterMs),
    /** When prices were last fetched successfully, or 0. */
    fetchedAt: query.dataUpdatedAt,
    newestObservedAt,
    retryNow: () => {
      void refetch();
    },
  };
}
