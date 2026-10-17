import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';

// Run on a trusted workstation only. Never ship Admin SDK credentials in Web/Android.
const [projectId, uid, employeeCode, displayName] = process.argv.slice(2);
if (!projectId || !uid || !employeeCode || !displayName || uid.includes('/')) {
  throw new Error('Cách dùng: node bootstrap-admin.mjs PROJECT_ID UID MA_NV "Ho ten"');
}
initializeApp({ projectId });
const account = await getAuth().getUser(uid);
const db = getFirestore();
await db.runTransaction(async tx => {
  const staff = await tx.get(db.collection('staff').limit(1));
  if (!staff.empty) throw new Error('Đã có nhân viên. Dùng trang Nhân viên web để cấp/đổi quyền.');
  tx.create(db.collection('staff').doc(uid), { role: 'admin', active: true, employeeCode, displayName,
    email: account.email || '', createdAt: Date.now(), updatedAt: Date.now() });
});
console.log(`Đã cấp quản trị đầu tiên cho UID ${uid} tại project ${projectId}`);
