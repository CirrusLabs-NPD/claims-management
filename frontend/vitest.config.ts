import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Test runner config kept separate from vite.config.ts so the production
// build (tsc -b && vite build) is untouched. Component tests run in jsdom.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
