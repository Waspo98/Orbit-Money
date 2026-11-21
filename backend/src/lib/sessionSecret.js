import crypto from 'crypto';
import fs from 'fs';
import path from 'path';

/**
 * Returns true when a secret value is blank or still contains template placeholders.
 */
export function looksLikePlaceholder(value) {
  const text = String(value || '').trim().toLowerCase();
  return !text || text.includes('change_me') || text.includes('change_this');
}

/**
 * Resolves the Express session secret from env, or persists a generated secret in dataDir.
 */
export function resolveSessionSecret({ envSecret, dataDir }) {
  const trimmed = String(envSecret || '').trim();
  if (trimmed) {
    if (looksLikePlaceholder(trimmed)) {
      const err = new Error('SESSION_SECRET still contains a placeholder value.');
      err.code = 'PLACEHOLDER_SECRET';
      throw err;
    }
    return { secret: trimmed, source: 'env' };
  }

  const secretPath = path.join(dataDir, '.session_secret');
  fs.mkdirSync(dataDir, { recursive: true });

  if (fs.existsSync(secretPath)) {
    const existing = fs.readFileSync(secretPath, 'utf8').trim();
    if (existing && !looksLikePlaceholder(existing)) {
      return { secret: existing, source: 'file', filePath: secretPath };
    }
  }

  const generated = crypto.randomBytes(32).toString('hex');
  fs.writeFileSync(secretPath, `${generated}\n`, { encoding: 'utf8', mode: 0o600 });
  return { secret: generated, source: 'generated', filePath: secretPath };
}
