import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    // npm run dev: same /api path as the nginx image, forwarded to the gateway
    proxy: {
      '/api': process.env.VITE_API_PROXY ?? 'http://localhost:8888',
    },
  },
  preview: {
    host: '0.0.0.0',
  },
});
