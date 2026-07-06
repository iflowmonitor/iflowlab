import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Relative base so the SPA works when served from the Quarkus jar at any context.
export default defineConfig({
  plugins: [react()],
  base: "./",
  build: {
    outDir: "dist",
    emptyOutDir: true,
    chunkSizeWarningLimit: 4000,
  },
  server: {
    // Dev-only: proxy API calls to the Quarkus backend (Quinoa dev mode).
    proxy: {
      "/run": "http://127.0.0.1:8080",
      "/workspace": "http://127.0.0.1:8080",
    },
  },
});
