import express from 'express';
import session from 'express-session';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { config } from './config.js';
import { db, runMigrations } from './db/index.js';
import { requireAuth, requireWriteForUnsafeMethods } from './auth.js';
import { startScheduler } from './scheduler.js';
import { seedDemoData } from './services/demoSeed.js';
import BetterSqliteSessionStore from './services/sessionStore.js';
import authRoutes from './routes/auth.js';
import healthRoutes from './routes/health.js';
import importRoutes from './routes/import.js';
import transactionsRoutes from './routes/transactions.js';
import accountsRoutes from './routes/accounts.js';
import categoriesRoutes from './routes/categories.js';
import rulesRoutes from './routes/rules.js';
import simplefinRoutes from './routes/simplefin.js';
import budgetsRoutes from './routes/budgets.js';
import spendingTrendsRoutes from './routes/spendingTrends.js';
import netWorthRoutes from './routes/netWorth.js';
import mhaRoutes from './routes/mha.js';
import goalsRoutes from './routes/goals.js';
import householdRoutes from './routes/household.js';
import householdSharingRoutes from './routes/householdSharing.js';
import merchantLogosRoutes from './routes/merchantLogos.js';
import upcomingRoutes from './routes/upcoming.js';
import dataRoutes from './routes/data.js';
import preferencesRoutes from './routes/preferences.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// --- Startup ----------------------------------------------------------------
console.log('Orbit Money starting...');
console.log('Running migrations...');
runMigrations();
if (config.seedDemoData) {
  seedDemoData(db);
}

// --- Express app ------------------------------------------------------------
const app = express();
app.use(express.json({ limit: '10mb' }));

// Session store: separate sessions.db file in the data volume.
app.use(
  session({
    name: config.sessionName,
    store: new BetterSqliteSessionStore({
      db: 'sessions.db',
      dir: config.dataDir,
      wal: true
    }),
    secret: config.sessionSecret,
    resave: false,
    saveUninitialized: false,
    cookie: {
      maxAge: 10 * 365 * 24 * 60 * 60 * 1000,
      httpOnly: true,
      sameSite: 'lax'
    }
  })
);

// --- Public routes (no auth) ------------------------------------------------
app.use('/api/auth', authRoutes);
app.use('/api/health', healthRoutes);

// --- Protected routes -------------------------------------------------------
app.use('/api', requireAuth);
app.use('/api/preferences', preferencesRoutes);
app.use('/api', requireWriteForUnsafeMethods);
app.use('/api/import', importRoutes);
app.use('/api/transactions', transactionsRoutes);
app.use('/api/accounts', accountsRoutes);
app.use('/api/categories', categoriesRoutes);
app.use('/api/rules', rulesRoutes);
app.use('/api/simplefin', simplefinRoutes);
app.use('/api/budgets', budgetsRoutes);
app.use('/api/spending-trends', spendingTrendsRoutes);
app.use('/api/net-worth', netWorthRoutes);
app.use('/api/mha', mhaRoutes);
app.use('/api/goals', goalsRoutes);
app.use('/api/household', householdRoutes);
app.use('/api/household-sharing', householdSharingRoutes);
app.use('/api/merchant-logos', merchantLogosRoutes);
app.use('/api/upcoming', upcomingRoutes);
app.use('/api/data', dataRoutes);

// Any other /api/* is a 404.
app.use('/api', requireAuth, (req, res) => {
  res.status(404).json({ error: 'Not implemented yet' });
});

// --- Frontend static files --------------------------------------------------
const publicDir = path.join(__dirname, '..', 'public');
app.use(express.static(publicDir));

app.get(/^\/(?!api).*/, (req, res) => {
  if (!fs.existsSync(path.join(publicDir, 'index.html'))) {
    return res
      .status(404)
      .send(
        'Frontend dev server is separate in local development. Open http://localhost:5173 instead.'
      );
  }
  res.sendFile(path.join(publicDir, 'index.html'));
});

// --- Listen + start scheduler -----------------------------------------------
app.listen(config.port, () => {
  console.log(`Orbit Money listening on port ${config.port}`);
  console.log(`Data directory: ${config.dataDir}`);
  startScheduler();
});
