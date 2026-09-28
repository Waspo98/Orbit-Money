import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:5008';

function sqliteMigrationsPlugin() {
  const virtualModuleId = 'virtual:sqlite-migrations';
  const resolvedVirtualModuleId = '\0' + virtualModuleId;

  return {
    name: 'vite-plugin-sqlite-migrations',
    resolveId(id) {
      if (id === virtualModuleId) {
        return resolvedVirtualModuleId;
      }
    },
    load(id) {
      if (id === resolvedVirtualModuleId) {
        const migrationsDir = path.resolve(__dirname, '../backend/src/db/migrations');
        if (!fs.existsSync(migrationsDir)) {
          return 'export default [];';
        }
        const files = fs.readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort();
        const migrations = files.map((file) => ({
          name: file,
          sql: fs.readFileSync(path.join(migrationsDir, file), 'utf-8')
        }));
        return `export default ${JSON.stringify(migrations)};`;
      }
    }
  };
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), sqliteMigrationsPlugin()],
  server: {
    // During `npm run dev`, proxy API calls to the Express backend.
    proxy: {
      '/api': apiProxyTarget
    },
    fs: {
      allow: ['..']
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
});
