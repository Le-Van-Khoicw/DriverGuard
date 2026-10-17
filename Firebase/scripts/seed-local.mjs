import { createRequire } from 'node:module';
const require = createRequire(new URL('../functions/package.json', import.meta.url));
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');

// This command deliberately refuses real projects and remote Admin SDK endpoints.
if (process.env.GCLOUD_PROJECT && process.env.GCLOUD_PROJECT !== 'demo-driverguard') throw new Error('Chỉ dùng demo-driverguard');
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
initializeApp({ projectId: 'demo-driverguard' });
const auth = getAuth(), db = getFirestore(), now = Date.now();
const password = 'Demo123456!';
const accounts = [
  { uid: 'demo-admin', email: 'admin@driverguard.test', name: 'Quản lý demo', code: 'NV001', role: 'admin' },
  { uid: 'demo-dispatcher', email: 'dieuphoi@driverguard.test', name: 'Điều phối demo', code: 'NV002', role: 'dispatcher' },
  { uid: 'demo-driver', email: 'taixe@driverguard.test', name: 'Tài xế demo', code: 'TX001' },
];
for (const a of accounts) {
  try { await auth.createUser({ uid: a.uid, email: a.email, password, displayName: a.name, emailVerified: true }); }
  catch (e) { if (e.code !== 'auth/uid-already-exists' && e.code !== 'auth/email-already-exists') throw e; }
  const ref = db.collection(a.role ? 'staff' : 'drivers').doc(a.uid);
  if (!(await ref.get()).exists) await ref.create(a.role ? { active: true, role: a.role, displayName: a.name, employeeCode: a.code, email: a.email, createdAt: now }
    : { uid: a.uid, displayName: a.name, employeeCode: a.code, email: a.email, phone: '0900000000', accountStatus: 'active', createdAt: now, updatedAt: now });
}
const vehicle = db.collection('fleetVehicles').doc('demo-truck');
await db.runTransaction(async tx => {
  if ((await tx.get(vehicle)).exists) return;
  tx.create(vehicle, { plate: 'DEMO-001', capacityKg: 5000, status: 'available', createdAt: now, updatedAt: now, updatedBy: 'demo-admin' });
  tx.create(db.collection('fleetVehiclePlates').doc('DEMO001'), { vehicleId: vehicle.id });
});
console.log('Tài khoản LOCAL (dữ liệu minh họa, không đăng nhập Firebase thật):');
for (const a of accounts) console.log(`  ${a.email} / ${password}`);
console.log('Đã có xe demo 5.000 kg. Tạo đơn và giao chuyến từ web để thấy luồng nghiệp vụ.');
