import 'dotenv/config';

export const config = {
  port: parseInt(process.env.PORT || '5008', 10),
  authProvider: process.env.AUTH_PROVIDER || 'local',
  adminUsername: process.env.ADMIN_USERNAME || 'admin',
  adminPassword: process.env.ADMIN_PASSWORD,
  sessionSecret: process.env.SESSION_SECRET,
  sessionName: process.env.SESSION_NAME || 'connect.sid',
  apiKey: process.env.API_KEY || null,
  authentikIssuerUrl: process.env.AUTHENTIK_ISSUER_URL || null,
  authentikClientId: process.env.AUTHENTIK_CLIENT_ID || null,
  authentikClientSecret: process.env.AUTHENTIK_CLIENT_SECRET || null,
  authentikRedirectUri: process.env.AUTHENTIK_REDIRECT_URI || null,
  simplefinEncryptionKey: process.env.SIMPLEFIN_ENCRYPTION_KEY,
  logoDevPublishableKey: process.env.LOGO_DEV_PUBLISHABLE_KEY || null,
  logoDevSecretKey: process.env.LOGO_DEV_SECRET_KEY || null,
  dataDir: process.env.DATA_DIR || '/app/data',
  tz: process.env.TZ || 'America/Chicago',
  seedDemoData: process.env.SEED_DEMO_DATA === '1'
};

// Validate required env vars on startup — fail fast with a clear error.
const required = ['sessionSecret'];
if (config.authProvider !== 'authentik') {
  required.push('adminPassword');
}
const missing = required.filter((k) => !config[k]);

if (missing.length > 0) {
  console.error('FATAL: Missing required environment variables:');
  for (const k of missing) {
    const envName = k.replace(/[A-Z]/g, (c) => '_' + c).toUpperCase();
    console.error(`  - ${envName}`);
  }
  console.error('\nCheck your .env file against .env.example.');
  process.exit(1);
}

if (!config.simplefinEncryptionKey) {
  console.warn('WARNING: SIMPLEFIN_ENCRYPTION_KEY not set. SimpleFIN sync will be unavailable until it is configured.');
}

if (config.authProvider === 'authentik') {
  const oidcRequired = [
    'authentikIssuerUrl',
    'authentikClientId',
    'authentikClientSecret',
    'authentikRedirectUri'
  ];
  const oidcMissing = oidcRequired.filter((k) => !config[k]);
  if (oidcMissing.length > 0) {
    console.error('FATAL: Missing Authentik/OIDC environment variables:');
    for (const k of oidcMissing) {
      const envName = k.replace(/[A-Z]/g, (c) => '_' + c).toUpperCase();
      console.error(`  - ${envName}`);
    }
    process.exit(1);
  }
}
