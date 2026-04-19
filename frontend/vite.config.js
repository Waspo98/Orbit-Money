import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // During `npm run dev`, proxy API calls to the Express backend.
    proxy: {
      '/api': 'http://localhost:5008'
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
});
