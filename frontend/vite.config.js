import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:5008';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // During `npm run dev`, proxy API calls to the Express backend.
    proxy: {
      '/api': apiProxyTarget
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
});
