import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Encaminha /api para o backend local (evita CORS no desenvolvimento).
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
