import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [tailwindcss(), react()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['tests/**/*.behavior.test.{ts,tsx}'],
    setupFiles: ['./vitest.setup.ts'],
  },
});
