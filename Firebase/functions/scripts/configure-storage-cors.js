import { getStorage } from 'firebase-admin/storage';
import { initializeFirebase } from '../runtime.js';

const origins = (process.env.ALLOWED_ORIGINS || '').split(',').map(s => s.trim()).filter(Boolean);
if (!origins.length || origins.some(s => !/^https?:\/\/[^/]+$/.test(s))) throw new Error('Set ALLOWED_ORIGINS to exact web origins');
if (process.env.FIREBASE_STORAGE_EMULATOR_HOST) throw new Error('Real bucket only');
initializeFirebase();
// CORS enables authenticated browser downloads. Storage Rules still decide who may read.
await getStorage().bucket().setCorsConfiguration([{ origin: origins, method: ['GET', 'HEAD'], responseHeader: ['Content-Type', 'Content-Disposition'], maxAgeSeconds: 3600 }]);
console.log('Configured authenticated browser downloads for the specified origins.');
