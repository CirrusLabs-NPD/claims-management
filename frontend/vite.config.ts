import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Local dev without Docker: proxy the API so the browser still sees one origin.
    proxy: {
      '/api': { target: 'http://localhost:9090', changeOrigin: true },
    },
  },
  build: { outDir: 'dist', sourcemap: false },
});
