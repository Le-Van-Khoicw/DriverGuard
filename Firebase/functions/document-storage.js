import { randomUUID } from 'node:crypto';
import { createClient } from '@supabase/supabase-js';
import { getStorage } from 'firebase-admin/storage';
import { getFirestore } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/v2/https';

export const documentKinds = ['license_front', 'license_back', 'identity_front', 'identity_back', 'health'];
export const requiredDocumentKinds = ['license_front', 'license_back'];
export const maxDocumentBytes = 5 * 1024 * 1024;
const types = ['image/jpeg', 'image/png', 'application/pdf'];
const fail = (message, code = 'invalid-argument') => { throw new HttpsError(code, message); };
let client;
function supabase() {
  if (!process.env.SUPABASE_URL || !process.env.SUPABASE_SERVICE_ROLE_KEY) fail('Quản lý cần cấu hình Supabase trên backend để lưu giấy tờ.', 'failed-precondition');
  client ??= createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { fetch: (url, options) => fetch(url, { ...options, signal: AbortSignal.timeout(25_000) }) },
  });
  return client.storage;
}
const bucketName = () => process.env.SUPABASE_DOCUMENT_BUCKET || 'driver-documents';
async function privateBucket() {
  const storage = supabase();
  const { data, error } = await storage.getBucket(bucketName());
  if (error) fail('Không truy cập được kho giấy tờ. Kiểm tra bucket và khóa Supabase trên server.', 'unavailable');
  if (data.public !== false) fail('Kho giấy tờ phải đặt Private, không được Public.', 'failed-precondition');
  return storage.from(bucketName());
}
export function parseDocumentPath(path) {
  if (typeof path !== 'string' || path.length > 500) fail('Đường dẫn giấy tờ không hợp lệ');
  const provider = path.startsWith('supabase:') ? 'supabase' : 'firebase';
  const key = provider === 'supabase' ? path.slice(9) : path;
  const parts = key.split('/');
  if (parts.length !== 4 || parts[0] !== 'driver-documents' || !parts[1] || ['.', '..'].includes(parts[1]) ||
      !documentKinds.includes(parts[2]) || !/^[A-Za-z0-9-]{1,128}$/.test(parts[3])) fail('Đường dẫn giấy tờ không hợp lệ');
  return { provider, key, uid: parts[1], kind: parts[2] };
}
export async function documentMetadata(path) {
  const parsed = parseDocumentPath(path);
  let metadata;
  if (parsed.provider === 'supabase') {
    const bucket = await privateBucket();
    const { data, error } = await bucket.info(parsed.key);
    if (error) fail('Chưa tìm thấy giấy tờ trên Supabase. Chọn và tải lại tệp.');
    metadata = { size: Number(data.size), contentType: data.contentType };
  } else {
    // Existing Firebase/emulator dossiers remain explicitly identified, never relabeled as Supabase.
    const bucket = getStorage().bucket(process.env.FUNCTIONS_EMULATOR === 'true' ? 'demo-driverguard.appspot.com' : process.env.FIREBASE_STORAGE_BUCKET);
    try { const [data] = await bucket.file(parsed.key).getMetadata(); metadata = { size: Number(data.size), contentType: data.contentType }; }
    catch { fail('Tệp Firebase cũ chưa truy cập được. Tải lại tệp sang kho Supabase.'); }
  }
  if (!types.includes(metadata.contentType) || !Number.isFinite(metadata.size) || metadata.size <= 0 || metadata.size > maxDocumentBytes) fail('Chỉ nhận JPG, PNG, PDF từ 1 byte đến 5 MB');
  return metadata;
}
export async function requireDocumentWriter(uid) {
  const driver = (await getFirestore().doc(`drivers/${uid}`).get()).data();
  if (!driver || !['pending', 'active'].includes(driver.accountStatus)) fail('Tài khoản tài xế chưa được phép tải giấy tờ.', 'permission-denied');
}
export async function requireDocumentReader(uid, path) {
  const parsed = parseDocumentPath(path);
  if (uid === parsed.uid) return;
  const staff = (await getFirestore().doc(`staff/${uid}`).get()).data();
  if (staff?.active !== true || staff.role !== 'admin') fail('Chỉ chủ hồ sơ và quản trị được xem giấy tờ.', 'permission-denied');
}
export async function uploadDocument(uid, kind, contentType, bytes) {
  if (!documentKinds.includes(kind) || !types.includes(contentType) || !bytes.length || bytes.length > maxDocumentBytes) fail('Chỉ nhận JPG, PNG, PDF tối đa 5 MB và đúng loại giấy tờ');
  const matches = contentType === 'image/jpeg' ? bytes.subarray(0, 3).equals(Buffer.from([255, 216, 255]))
    : contentType === 'image/png' ? bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
      : bytes.subarray(0, 5).toString('ascii') === '%PDF-';
  if (!matches) fail('Nội dung tệp không khớp JPG, PNG hoặc PDF đã chọn');
  const key = `driver-documents/${uid}/${kind}/${randomUUID()}`;
  const bucket = await privateBucket();
  // A new immutable key protects previously reviewed files from replacement.
  const { error } = await bucket.upload(key, bytes, { contentType, upsert: false, cacheControl: '0' });
  if (error) fail('Không tải được giấy tờ. Kiểm tra dung lượng kho và kết nối rồi thử lại.', 'unavailable');
  return { path: `supabase:${key}`, kind, size: bytes.length, contentType };
}
export async function downloadDocument(path) {
  const parsed = parseDocumentPath(path);
  const metadata = await documentMetadata(path);
  let bytes;
  if (parsed.provider === 'supabase') {
    const bucket = await privateBucket();
    const { data, error } = await bucket.download(parsed.key);
    if (error) fail('Không tải được giấy tờ.', 'unavailable');
    bytes = Buffer.from(await data.arrayBuffer());
  } else {
    const bucket = getStorage().bucket(process.env.FIREBASE_STORAGE_BUCKET);
    [bytes] = await bucket.file(parsed.key).download();
  }
  if (bytes.length > maxDocumentBytes) fail('Tệp vượt 5 MB');
  return { bytes, contentType: metadata.contentType };
}
