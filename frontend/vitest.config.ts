import { defineConfig } from 'vitest/config';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [tailwindcss()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['tests/**/*.behavior.test.{ts,tsx}'],
    setupFiles: ['./vitest.setup.ts'],
    testTimeout: 10_000,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      exclude: ['src/api.ts', 'src/main.tsx', 'src/vite-env.d.ts'],
      thresholds: {
        statements: 48,
        branches: 38,
        functions: 50,
        lines: 50,
      },
    },
  },
});
