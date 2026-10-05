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
    // Coverage floor: `test:run` fails below 80%. Raise tests, never lower these numbers.
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/test/**", "src/main.tsx", "src/**/*.d.ts"],
      reporter: ["text-summary", "html"],
      thresholds: { lines: 80, statements: 80, functions: 80, branches: 80 },
    },
  },
});
