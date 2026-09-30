/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Where API calls go; see `.env.example`. */
  readonly VITE_API_BASE_URL?: string;
  /** How often the reference-price panel refreshes, in milliseconds. */
  readonly VITE_MARKET_DATA_POLL_MS?: string;
  /** How old prices may be before they are shown as stale, in milliseconds. */
  readonly VITE_MARKET_DATA_STALE_MS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
