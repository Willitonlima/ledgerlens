import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig(({ mode }) => {
  const apiUrl = loadEnv(mode, process.cwd(), "VITE_").VITE_API_URL?.trim();
  if (mode === "production") {
    if (!apiUrl) {
      throw new Error("Defina VITE_API_URL com a origem HTTPS da API antes do build de produção.");
    }
    const apiUrlWithoutTrailingSlash = apiUrl.endsWith("/") ? apiUrl.slice(0, -1) : apiUrl;
    let apiOrigin;
    try {
      apiOrigin = new URL(apiUrlWithoutTrailingSlash);
    } catch {
      throw new Error("VITE_API_URL deve ser uma URL HTTPS válida, sem caminho ou /api.");
    }
    if (apiOrigin.protocol !== "https:" || apiOrigin.origin !== apiUrlWithoutTrailingSlash) {
      throw new Error("VITE_API_URL deve conter somente a origem HTTPS da API, sem caminho ou query.");
    }
  }

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      // Encaminha /api para o backend local (evita CORS no desenvolvimento).
      proxy: {
        "/api": "http://localhost:8080",
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setup.js",
      restoreMocks: true,
    },
  };
});
