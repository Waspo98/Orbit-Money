// =============================================================================
// crypto.js — AES-256-GCM helpers for encrypting the SimpleFIN access URL.
// =============================================================================
// The access URL is essentially a password (embeds basic-auth credentials for
// the SimpleFIN Bridge). We encrypt it at rest with a key from .env so a DB
// leak alone isn't enough to hijack the sync connection.
//
// Format: base64( iv(12) | authTag(16) | ciphertext )
// =============================================================================

import crypto from 'crypto';
import { config } from './config.js';

const ALGO = 'aes-256-gcm';
const IV_LENGTH = 12;   // GCM standard
const TAG_LENGTH = 16;

function getKey() {
  const hex = config.simplefinEncryptionKey;
  if (!hex) {
    throw new Error(
      'SIMPLEFIN_ENCRYPTION_KEY is not set. Add it to your .env file.'
    );
  }
  const buf = Buffer.from(hex, 'hex');
  if (buf.length !== 32) {
    throw new Error(
      `SIMPLEFIN_ENCRYPTION_KEY must be 64 hex chars (32 bytes). Got ${buf.length} bytes.`
    );
  }
  return buf;
}

export function encrypt(plaintext) {
  const key = getKey();
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv(ALGO, key, iv);
  const ct = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, ct]).toString('base64');
}

export function decrypt(blob) {
  const key = getKey();
  const buf = Buffer.from(blob, 'base64');
  const iv = buf.subarray(0, IV_LENGTH);
  const tag = buf.subarray(IV_LENGTH, IV_LENGTH + TAG_LENGTH);
  const ct = buf.subarray(IV_LENGTH + TAG_LENGTH);
  const decipher = crypto.createDecipheriv(ALGO, key, iv);
  decipher.setAuthTag(tag);
  const pt = Buffer.concat([decipher.update(ct), decipher.final()]);
  return pt.toString('utf8');
}
