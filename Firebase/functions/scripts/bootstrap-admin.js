import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { initializeFirebase } from '../runtime.js';

// One-time provisioning is performed from a trusted terminal, never via a public signup endpoint.
const [uid, displayName, employeeCode] = process.argv.slice(2);
if (!uid || uid.includes('/') || !displayName || !employeeCode) throw new Error('Usage: npm run bootstrap:admin -- FIREBASE_UID "Full name" NV001');
if (process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.FIRESTORE_EMULATOR_HOST) throw new Error('This command is for real Firebase only');
initializeFirebase();
const account = await getAuth().getUser(uid);
if (account.disabled || !account.emailVerified) throw new Error('Admin must have a verified email and an enabled Auth account');
const db = getFirestore();
await db.runTransaction(async tx => {
  // A single bootstrap marker serializes first-admin creation; future grants use saveStaff.
  const markerRef = db.doc('system/bootstrapAdmin');
  const target = db.doc(`staff/${uid}`);
  const [marker, staff] = await tx.getAll(markerRef, target);
  const admins = await tx.get(db.collection('staff').where('role', '==', 'admin'));
  if (marker.exists || !admins.empty || staff.exists) throw new Error('Staff/admin already exists. Use the current admin to grant roles.');
  tx.create(target, { role: 'admin', active: true, displayName, employeeCode, email: account.email || '', phone: '', createdAt: Date.now(), updatedAt: Date.now() });
  tx.create(markerRef, { uid, at: Date.now() });
});
console.log('Initial admin provisioned for the requested UID. No passwords changed.');
