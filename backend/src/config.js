import 'dotenv/config';

function normalizeAuthProvider(value) {
  const provider = String(value || 'local').trim().toLowerCase();
  if (provider === 'authentik') return 'oidc';
  return provider;
}

export const config = {
  port: parseInt(process.env.PORT || '5008', 10),
  authProvider: normalizeAuthProvider(process.env.AUTH_PROVIDER),
  adminUsername: process.env.ADMIN_USERNAME || 'admin',
  adminPassword: process.env.ADMIN_PASSWORD,
  sessionSecret: process.env.SESSION_SECRET,
  sessionName: process.env.SESSION_NAME || 'connect.sid',
  apiKey: process.env.API_KEY || null,
  oidcIssuerUrl: process.env.OIDC_ISSUER_URL || process.env.AUTHENTIK_ISSUER_URL || null,
  oidcClientId: process.env.OIDC_CLIENT_ID || process.env.AUTHENTIK_CLIENT_ID || null,
  oidcClientSecret: process.env.OIDC_CLIENT_SECRET || process.env.AUTHENTIK_CLIENT_SECRET || null,
  oidcRedirectUri: process.env.OIDC_REDIRECT_URI || process.env.AUTHENTIK_REDIRECT_URI || null,
  oidcScopes: process.env.OIDC_SCOPES || 'openid email profile',
  oidcLoginLabel: process.env.OIDC_LOGIN_LABEL || 'Log in with OIDC',
  simplefinEncryptionKey: process.env.SIMPLEFIN_ENCRYPTION_KEY,
  logoDevPublishableKey: process.env.LOGO_DEV_PUBLISHABLE_KEY || null,
  logoDevSecretKey: process.env.LOGO_DEV_SECRET_KEY || null,
  dataDir: process.env.DATA_DIR || '/app/data',
  tz: process.env.TZ || 'America/Chicago',
  seedDemoData: process.env.SEED_DEMO_DATA === '1'
};

// Validate required env vars on startup; fail fast with a clear error.
if (!['local', 'oidc', 'both'].includes(config.authProvider)) {
  console.error(`FATAL: AUTH_PROVIDER must be "local", "oidc", or "both". Received "${config.authProvider}".`);
  process.exit(1);
}

const required = ['sessionSecret'];
if (config.authProvider === 'local' || config.authProvider === 'both') {
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

if (config.authProvider === 'oidc' || config.authProvider === 'both') {
  const oidcRequired = [
    'oidcIssuerUrl',
    'oidcClientId',
    'oidcClientSecret',
    'oidcRedirectUri'
  ];
  const oidcMissing = oidcRequired.filter((k) => !config[k]);
  if (oidcMissing.length > 0) {
    console.error('FATAL: Missing OIDC environment variables:');
    for (const k of oidcMissing) {
      const envName = k.replace(/[A-Z]/g, (c) => '_' + c).toUpperCase();
      console.error(`  - ${envName}`);
    }
    process.exit(1);
  }
}
