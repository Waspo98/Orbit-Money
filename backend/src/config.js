import 'dotenv/config';

function normalizeAuthProvider(value) {
  const provider = String(value || 'local').trim().toLowerCase();
  if (provider === 'authentik') return 'oidc';
  return provider;
}

function cleanOptional(value) {
  const text = String(value || '').trim();
  return text ? text : null;
}

function looksLikePlaceholder(value) {
  const text = String(value || '').trim().toLowerCase();
  return !text || text.includes('change_me') || text.includes('change_this');
}

export const config = {
  port: parseInt(process.env.PORT || '5008', 10),
  authProvider: normalizeAuthProvider(process.env.AUTH_PROVIDER),
  adminUsername: process.env.ADMIN_USERNAME || 'admin',
  adminPassword: process.env.ADMIN_PASSWORD,
  sessionSecret: process.env.SESSION_SECRET,
  sessionName: process.env.SESSION_NAME || 'connect.sid',
  apiKey: cleanOptional(process.env.API_KEY),
  oidcIssuerUrl: process.env.OIDC_ISSUER_URL || process.env.AUTHENTIK_ISSUER_URL || null,
  oidcClientId: process.env.OIDC_CLIENT_ID || process.env.AUTHENTIK_CLIENT_ID || null,
  oidcClientSecret: process.env.OIDC_CLIENT_SECRET || process.env.AUTHENTIK_CLIENT_SECRET || null,
  oidcRedirectUri: process.env.OIDC_REDIRECT_URI || process.env.AUTHENTIK_REDIRECT_URI || null,
  oidcScopes: process.env.OIDC_SCOPES || 'openid email profile',
  oidcLoginLabel: process.env.OIDC_LOGIN_LABEL || 'Log in with OIDC',
  simplefinEncryptionKey: process.env.SIMPLEFIN_ENCRYPTION_KEY,
  logoDevPublishableKey: process.env.LOGO_DEV_PUBLISHABLE_KEY || null,
  logoDevSecretKey: process.env.LOGO_DEV_SECRET_KEY || null,
  webPushPublicKey: cleanOptional(process.env.WEB_PUSH_PUBLIC_KEY),
  webPushPrivateKey: cleanOptional(process.env.WEB_PUSH_PRIVATE_KEY),
  webPushSubject: cleanOptional(process.env.WEB_PUSH_SUBJECT),
  dataDir: process.env.DATA_DIR || '/app/data',
  tz: process.env.TZ || 'America/Chicago',
  seedDemoData: process.env.SEED_DEMO_DATA === '1',
  sampleDataEnabled: process.env.ENABLE_SAMPLE_DATA === '1' || process.env.SEED_DEMO_DATA === '1'
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

const placeholderSecrets = required.filter((k) => looksLikePlaceholder(config[k]));
if (placeholderSecrets.length > 0) {
  console.error('FATAL: Replace placeholder secret values before starting Orbit Money:');
  for (const k of placeholderSecrets) {
    const envName = k.replace(/[A-Z]/g, (c) => '_' + c).toUpperCase();
    console.error(`  - ${envName}`);
  }
  process.exit(1);
}

if (process.env.API_KEY && looksLikePlaceholder(process.env.API_KEY)) {
  console.error('FATAL: API_KEY is optional, but it must be a real secret when set.');
  process.exit(1);
}

if (!config.simplefinEncryptionKey) {
  console.warn('WARNING: SIMPLEFIN_ENCRYPTION_KEY not set. SimpleFIN sync will be unavailable until it is configured.');
} else if (looksLikePlaceholder(config.simplefinEncryptionKey)) {
  console.error('FATAL: Replace the placeholder SIMPLEFIN_ENCRYPTION_KEY or leave it blank until SimpleFIN is configured.');
  process.exit(1);
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
