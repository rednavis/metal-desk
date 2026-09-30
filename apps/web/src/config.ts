/**
 * Settings read from the environment once, with their defaults. A number in a component is a
 * number nobody can change for a deployment, so none of these is written anywhere else.
 */

function positive(raw: string | undefined, fallback: number): number {
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

/** FR-1.1's illustrative refresh interval. */
const DEFAULT_POLL_MS = 20_000;

const pollIntervalMs = positive(import.meta.env.VITE_MARKET_DATA_POLL_MS, DEFAULT_POLL_MS);

export interface MarketDataConfig {
  /** How often prices are refreshed while the tab is visible. */
  pollIntervalMs: number;
  /** How old the last update, or the newest price, may be before the panel says it is stale. */
  staleAfterMs: number;
  /** The first retry delay after a failed refresh; it doubles up to `retryMaxMs`. */
  retryBaseMs: number;
  retryMaxMs: number;
}

export const marketDataConfig: MarketDataConfig = {
  pollIntervalMs,
  // Three intervals: one missed refresh is noise, three in a row is a problem worth showing.
  staleAfterMs: positive(import.meta.env.VITE_MARKET_DATA_STALE_MS, pollIntervalMs * 3),
  retryBaseMs: 2_000,
  retryMaxMs: 60_000,
};

/**
 * The version of the privacy policy the checkout shows and the customer accepts (BRD FR-4.3). The
 * server refuses an acceptance of any other version (`consent.version-mismatch`), so this must be
 * set to `metaldesk.checkout.policy-version` for a deployment; the default is the server's.
 */
export const policyVersion: string = import.meta.env.VITE_POLICY_VERSION ?? "2026-10";
