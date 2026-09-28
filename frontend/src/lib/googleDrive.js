import { exportDatabaseBytes, importDatabaseBytes } from '../db/localDb.js';

const DRIVE_APPDATA_SCOPE = 'https://www.googleapis.com/auth/drive.appdata';
const BACKUP_DB_FILENAME = 'orbit_money_database.sqlite';
const BACKUP_META_FILENAME = 'orbit_backup_meta.json';

const GOOGLE_CLIENT_ID_KEY = 'orbit_google_client_id';
const GOOGLE_TOKEN_KEY = 'orbit_google_drive_token';
const GOOGLE_USER_EMAIL_KEY = 'orbit_google_drive_email';

let gisInited = false;
let tokenClient = null;

export function getStoredGoogleClientId() {
  return localStorage.getItem(GOOGLE_CLIENT_ID_KEY) || '';
}

export function setStoredGoogleClientId(clientId) {
  if (clientId) {
    localStorage.setItem(GOOGLE_CLIENT_ID_KEY, clientId.trim());
  } else {
    localStorage.removeItem(GOOGLE_CLIENT_ID_KEY);
  }
}

export function getStoredGoogleAccountEmail() {
  return localStorage.getItem(GOOGLE_USER_EMAIL_KEY) || '';
}

export function isGoogleDriveConnected() {
  return Boolean(localStorage.getItem(GOOGLE_USER_EMAIL_KEY));
}

export function disconnectGoogleDrive() {
  localStorage.removeItem(GOOGLE_TOKEN_KEY);
  localStorage.removeItem(GOOGLE_USER_EMAIL_KEY);
}

function loadGisScript() {
  if (typeof window === 'undefined') return Promise.resolve(false);
  if (window.google?.accounts?.oauth2) return Promise.resolve(true);

  return new Promise((resolve) => {
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.head.appendChild(script);
  });
}

export async function requestGoogleAccessToken(customClientId) {
  const clientId = customClientId || getStoredGoogleClientId();
  if (!clientId) {
    throw new Error('Please configure a Google OAuth Client ID in Settings first.');
  }

  const loaded = await loadGisScript();
  if (!loaded || !window.google?.accounts?.oauth2) {
    throw new Error('Google Identity Services SDK could not be loaded.');
  }

  return new Promise((resolve, reject) => {
    try {
      tokenClient = window.google.accounts.oauth2.initTokenClient({
        client_id: clientId,
        scope: DRIVE_APPDATA_SCOPE,
        callback: async (resp) => {
          if (resp.error) {
            reject(new Error(resp.error_description || resp.error));
            return;
          }
          if (resp.access_token) {
            localStorage.setItem(GOOGLE_TOKEN_KEY, resp.access_token);
            // Fetch user profile email
            try {
              const userRes = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
                headers: { Authorization: `Bearer ${resp.access_token}` }
              });
              if (userRes.ok) {
                const info = await userRes.json();
                if (info.email) {
                  localStorage.setItem(GOOGLE_USER_EMAIL_KEY, info.email);
                }
              }
            } catch {}
            resolve(resp.access_token);
          } else {
            reject(new Error('No access token received from Google.'));
          }
        }
      });
      tokenClient.requestAccessToken({ prompt: 'consent' });
    } catch (err) {
      reject(err);
    }
  });
}

async function getValidToken() {
  const token = localStorage.getItem(GOOGLE_TOKEN_KEY);
  if (token) return token;
  return requestGoogleAccessToken();
}

async function driveApiRequest(url, options = {}) {
  let token = await getValidToken();
  let res = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(options.headers || {})
    }
  });

  if (res.status === 401) {
    // Token expired, re-request
    token = await requestGoogleAccessToken();
    res = await fetch(url, {
      ...options,
      headers: {
        Authorization: `Bearer ${token}`,
        ...(options.headers || {})
      }
    });
  }

  if (!res.ok) {
    const errData = await res.json().catch(() => ({}));
    throw new Error(errData?.error?.message || `Google Drive API error: ${res.status}`);
  }

  return res;
}

export async function findAppDataFile(name) {
  const url = `https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='${encodeURIComponent(name)}' and trashed=false&fields=files(id,name,modifiedTime,size)`;
  const res = await driveApiRequest(url);
  const data = await res.json();
  return data.files?.[0] || null;
}

export async function getDriveBackupInfo() {
  try {
    const file = await findAppDataFile(BACKUP_DB_FILENAME);
    if (!file) return { exists: false };
    return {
      exists: true,
      fileId: file.id,
      lastModified: file.modifiedTime,
      sizeBytes: Number(file.size || 0)
    };
  } catch (err) {
    console.warn('Failed to check Google Drive backup info:', err);
    return { exists: false, error: err.message };
  }
}

export async function uploadBackupToGoogleDrive() {
  const bytes = await exportDatabaseBytes();
  const existing = await findAppDataFile(BACKUP_DB_FILENAME);

  if (existing?.id) {
    // Update existing file content
    const uploadUrl = `https://www.googleapis.com/upload/drive/v3/files/${existing.id}?uploadType=media`;
    await driveApiRequest(uploadUrl, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/x-sqlite3' },
      body: bytes
    });
  } else {
    // Create new file inside appDataFolder
    const metadata = {
      name: BACKUP_DB_FILENAME,
      parents: ['appDataFolder']
    };
    const boundary = '-------314159265358979323846';
    const delimiter = `\r\n--${boundary}\r\n`;
    const closeDelimiter = `\r\n--${boundary}--`;

    const metadataPart = `${delimiter}Content-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(metadata)}`;
    const mediaHeader = `${delimiter}Content-Type: application/x-sqlite3\r\n\r\n`;

    const enc = new TextEncoder();
    const metaBytes = enc.encode(metadataPart);
    const mediaHeaderBytes = enc.encode(mediaHeader);
    const closeBytes = enc.encode(closeDelimiter);

    const totalLen = metaBytes.length + mediaHeaderBytes.length + bytes.length + closeBytes.length;
    const bodyBuffer = new Uint8Array(totalLen);

    let offset = 0;
    bodyBuffer.set(metaBytes, offset); offset += metaBytes.length;
    bodyBuffer.set(mediaHeaderBytes, offset); offset += mediaHeaderBytes.length;
    bodyBuffer.set(bytes, offset); offset += bytes.length;
    bodyBuffer.set(closeBytes, offset);

    const uploadUrl = 'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart';
    await driveApiRequest(uploadUrl, {
      method: 'POST',
      headers: {
        'Content-Type': `multipart/related; boundary=${boundary}`
      },
      body: bodyBuffer
    });
  }

  return { success: true, timestamp: new Date().toISOString() };
}

export async function restoreBackupFromGoogleDrive() {
  const existing = await findAppDataFile(BACKUP_DB_FILENAME);
  if (!existing?.id) {
    throw new Error('No Orbit Money backup found in your Google Drive AppData folder.');
  }

  const downloadUrl = `https://www.googleapis.com/drive/v3/files/${existing.id}?alt=media`;
  const res = await driveApiRequest(downloadUrl);
  const arrayBuffer = await res.arrayBuffer();
  const bytes = new Uint8Array(arrayBuffer);

  await importDatabaseBytes(bytes);
  return { success: true, restoredAt: new Date().toISOString() };
}

export function downloadLocalBackupFile() {
  exportDatabaseBytes().then((bytes) => {
    const blob = new Blob([bytes], { type: 'application/x-sqlite3' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `orbit-money-backup-${new Date().toISOString().slice(0, 10)}.sqlite`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  });
}

export async function restoreFromFile(file) {
  if (!file) throw new Error('No file selected.');
  const arrayBuffer = await file.arrayBuffer();
  const bytes = new Uint8Array(arrayBuffer);

  // Check if it's a JSON backup
  try {
    const text = new TextDecoder().decode(bytes.slice(0, 100));
    if (text.trim().startsWith('{')) {
      const fullText = new TextDecoder().decode(bytes);
      const json = JSON.parse(fullText);
      if (json.type === 'orbit_money_backup') {
        const { handleLocalApiRequest } = await import('../db/localApi.js');
        return handleLocalApiRequest('/api/data/orbit-restore', {
          method: 'POST',
          body: JSON.stringify(json)
        });
      }
    }
  } catch {}

  // Otherwise import directly as SQLite binary
  return importDatabaseBytes(bytes);
}
