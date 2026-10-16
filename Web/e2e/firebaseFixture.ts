import { type Page } from '@playwright/test';

// Serve SDK doubles only to the test browser. Production source is unchanged.
// A single ES module owns the store, so Auth/Firestore imports share state.
const fakeSdk = String.raw`
const now = Date.now();
const store = {
  drivers: {
    'driver-1': { displayName: 'Nguyễn An', email: 'an@example.com', phone: '0901234567', accountStatus: 'active', vehicleName: 'Xe An', licensePlate: '51A-12345', deviceName: 'Camera An', deviceCode: 'CAM-001', createdAt: now, updatedAt: now },
    'pending-1': { displayName: 'Nhân viên chờ duyệt', email: 'pending@example.com', accountStatus: 'pending', createdAt: now, updatedAt: now }
  },
  alerts: { 'alert-1': { userId: 'driver-1', ear: 0.15, timestamp: now, status: 'NEW', latitude: 10.75, longitude: 106.7 } },
  trips: { 'trip-1': { userId: 'driver-1', startTime: now } },
  deliveries: {}, employeeMessages: {}, transportSettings: {}, deliveryScores: {}, deliveryReviews: {}
};
const listeners = new Set();
function snap(ref) {
  if (ref.id) return { id: ref.id, exists: () => !!store[ref.name]?.[ref.id], data: () => store[ref.name]?.[ref.id] };
  return { docs: Object.entries(store[ref.name] || {}).map(([id, data]) => ({ id, data: () => data })) };
}
function notify(name) { for (const l of listeners) if (l.ref.name === name) l.next(snap(l.ref)); }
export const getFirestore = () => ({});
export const getAuth = () => ({ currentUser: { uid: 'admin-test' } });
export const collection = (_db, name) => ({ name });
export const doc = (_db, name, id) => ({ name, id });
export const query = (ref, ...constraints) => ref;
export const orderBy = (...args) => args;
export const where = (...args) => args;
export const limit = (...args) => args;
export const getDocs = async ref => snap(ref);
export const getDoc = async ref => snap(ref);
export const setDoc = async (ref, data) => { store[ref.name] ||= {}; store[ref.name][ref.id] = data; notify(ref.name); };
export const updateDoc = async (ref, data) => { if (!store[ref.name]?.[ref.id]) throw Error('Document not found'); Object.assign(store[ref.name][ref.id], data); notify(ref.name); };
export const deleteDoc = async ref => { delete store[ref.name][ref.id]; notify(ref.name); };
export const addDoc = async (ref, data) => { const id = crypto.randomUUID(); await setDoc({ ...ref, id }, data); return { id }; };
export const onSnapshot = (ref, next) => { const l = { ref, next }; listeners.add(l); queueMicrotask(() => { if (listeners.has(l)) next(snap(ref)); }); return () => listeners.delete(l); };
export class Timestamp { constructor(seconds) { this.seconds = seconds; } toDate() { return new Date(this.seconds * 1000); } toMillis() { return this.seconds * 1000; } }
export const signInWithEmailAndPassword = async (_auth, email, password) => { if (password === 'wrong') throw Error('Invalid credentials'); return { user: { getIdToken: async () => 'test-token' } }; };
export const signOut = async () => {};
export const runTransaction = async (_db, callback) => callback({ get: getDoc, set: setDoc });
`;
export async function mockFirebase(page: Page) {
  await page.route('**/__test__/firebase.js', route => route.fulfill({ contentType: 'application/javascript', body: fakeSdk }));
  await page.route(/\/node_modules\/\.vite\/deps\/firebase_(firestore|auth)\.js(?:\?|$)/, route => route.fulfill({ contentType: 'application/javascript', body: 'export * from "/__test__/firebase.js";' }));
  // Fail closed: a new SDK path must never fall back to the live project.
  await page.route(/https:\/\/[^/]*(googleapis\.com|firebaseio\.com|firebasestorage\.app)\//, route => route.abort());
  await page.route('**/src/app/mapBackground.ts*', route => route.fulfill({ contentType: 'application/javascript', body: 'export function addMapBackground() { return { remove() {} }; }' }));
}
