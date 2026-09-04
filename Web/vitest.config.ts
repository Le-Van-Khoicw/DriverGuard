import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/api/client.ts', 'src/app/App.tsx'],
      reporter: ['text', 'html', 'json-summary'],
      thresholds: { statements: 80, branches: 70, functions: 75, lines: 80 },
    },
  },
});
