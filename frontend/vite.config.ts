import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The browser calls /api on the same origin as the page, and Vite forwards those calls to the backend.
// So the backend needs no CORS setup. Point it at another backend with PAYCORE_API_URL.
const apiTarget = process.env.PAYCORE_API_URL ?? 'http://localhost:8080'
const proxy = { '/api': { target: apiTarget, changeOrigin: true } }

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true, proxy },
  preview: { port: 4173, strictPort: true, proxy },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
  },
})
