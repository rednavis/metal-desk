/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Where API calls go; see `.env.example`. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
