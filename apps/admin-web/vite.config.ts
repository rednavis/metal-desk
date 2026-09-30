import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// The dev server proxies `/api` to the backend, so the app calls a relative path and no backend
// address is compiled into the bundle. Point it elsewhere with API_PROXY_TARGET.
const backend = process.env.API_PROXY_TARGET ?? "http://localhost:8081";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { "/api": { target: backend, changeOrigin: true } },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
});
