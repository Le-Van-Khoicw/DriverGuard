import { applicationDefault, cert, getApps, initializeApp } from 'firebase-admin/app';

// Render receives the service account as a secret environment variable, never in the APK/web.
export function initializeFirebase() {
  if (getApps().length) return;
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  const options = { projectId: process.env.GCLOUD_PROJECT || process.env.FIREBASE_PROJECT_ID || 'driverguard-854e5' };
  if (raw) {
    let account;
    try { account = JSON.parse(raw); } catch { throw new Error('FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON'); }
    if (account.project_id !== options.projectId) throw new Error('Service account and FIREBASE_PROJECT_ID must match');
    options.credential = cert(account);
  } else if (!process.env.FUNCTIONS_EMULATOR) options.credential = applicationDefault();
  options.storageBucket = process.env.FIREBASE_STORAGE_BUCKET || 'driverguard-854e5.firebasestorage.app';
  initializeApp(options);
}
