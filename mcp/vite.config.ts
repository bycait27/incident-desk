import react from "@vitejs/plugin-react";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

const appRoot = fileURLToPath(new URL("./src", import.meta.url));

export default defineConfig({
  root: appRoot,
  plugins: [react(), viteSingleFile()],
  build: {
    outDir: resolve(appRoot, "../dist"),
    emptyOutDir: false,
    rollupOptions: {
      input: resolve(appRoot, "mcp-app.html"),
    },
  },
});
