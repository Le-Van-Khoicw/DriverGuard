import { createServer } from 'node:http';
import { HttpsError } from 'firebase-functions/v2/https';
import { getAuth } from 'firebase-admin/auth';
import { handleDispatchCommand, handleListCompanyAccounts } from './index.js';
import { maxDocumentBytes, requireDocumentWriter, requireDocumentReader, uploadDocument, downloadDocument } from './document-storage.js';

// The Firebase client SDK can call these URLs using the callable wire protocol.
// Business handlers stay shared with Functions so neither host bypasses permissions.
if (['FIREBASE_AUTH_EMULATOR_HOST', 'FIRESTORE_EMULATOR_HOST', 'FIREBASE_STORAGE_EMULATOR_HOST', 'FUNCTIONS_EMULATOR'].some(k => process.env[k])) {
  throw new Error('Online server refuses emulator environment variables');
}
if (!process.env.FIREBASE_SERVICE_ACCOUNT_JSON && !process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  throw new Error('Configure FIREBASE_SERVICE_ACCOUNT_JSON as a server secret');
}
const origins = new Set((process.env.ALLOWED_ORIGINS || '').split(',').map(s => s.trim()).filter(Boolean));
if (!origins.size) throw new Error('Configure ALLOWED_ORIGINS with the exact web origins');
const handlers = { '/dispatchCommand': handleDispatchCommand, '/listCompanyAccounts': handleListCompanyAccounts };
const codes = { 'invalid-argument': 400, unauthenticated: 401, 'permission-denied': 403, 'not-found': 404,
  'already-exists': 409, 'failed-precondition': 400, 'resource-exhausted': 429, unavailable: 503, 'deadline-exceeded': 504 };
const send = (res, status, body) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(body)); };
const bad = (message, code = 'invalid-argument') => { throw new HttpsError(code, message); };
function decode(value, depth = 0) {
  if (depth > 30) bad('Dữ liệu lồng nhau quá sâu');
  if (value === null || typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.map(item => decode(item, depth + 1));
  // Android callable SDK may wrap Kotlin Long as protobuf Int64Value.
  if (Object.hasOwn(value, '@type')) {
    if (!['type.googleapis.com/google.protobuf.Int64Value', 'type.googleapis.com/google.protobuf.UInt64Value'].includes(value['@type']) ||
        typeof value.value !== 'string' || !/^-?\d+$/.test(value.value)) bad('Kiểu số callable không hợp lệ');
    const number = Number(value.value);
    if (!Number.isSafeInteger(number) || (value['@type'].endsWith('UInt64Value') && number < 0)) bad('Số vượt giới hạn chính xác');
    return number;
  }
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, decode(item, depth + 1)]));
}
const server = createServer(async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  const origin = req.headers.origin;
  if (origin) {
    if (!origins.has(origin)) return send(res, 403, { error: { status: 'PERMISSION_DENIED', message: 'Web origin is not allowed' } });
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
  }
  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type, Firebase-Instance-ID-Token, X-Firebase-AppCheck');
    res.writeHead(204); return res.end();
  }
  const url = new URL(req.url, 'http://server');
  const pathname = url.pathname;
  if (req.method === 'GET' && pathname === '/health') return send(res, 200, { status: 'ok', projectId: process.env.FIREBASE_PROJECT_ID || 'driverguard-854e5', mode: 'online', features: { physicalDossiers: true, automaticStaffCodes: true, compactDossiers: true } });
  const handler = handlers[pathname];
  const documentRoute = pathname === '/driver-documents' && ['GET', 'POST'].includes(req.method);
  if (!documentRoute && (!handler || req.method !== 'POST')) return send(res, 404, { error: { status: 'NOT_FOUND', message: 'Endpoint not found' } });
  try {
    const match = /^Bearer (\S+)$/.exec(req.headers.authorization || '');
    if (!match) bad('Cần đăng nhập', 'unauthenticated');
    let token;
    // Revoked sessions and disabled accounts must not retain server privileges.
    try { token = await getAuth().verifyIdToken(match[1], true); }
    catch { bad('Phiên đăng nhập hết hạn hoặc không hợp lệ. Đăng nhập lại.', 'unauthenticated'); }
    if (documentRoute && req.method === 'GET') {
      const path = url.searchParams.get('path');
      await requireDocumentReader(token.uid, path);
      const file = await downloadDocument(path);
      res.writeHead(200, { 'Content-Type': file.contentType, 'Content-Length': file.bytes.length,
        'Content-Disposition': 'attachment; filename="driver-document"', 'Content-Security-Policy': "default-src 'none'; sandbox" });
      return res.end(file.bytes);
    }
    if (documentRoute) {
      await requireDocumentWriter(token.uid);
      const bytes = await readBody(req, maxDocumentBytes);
      const result = await uploadDocument(token.uid, url.searchParams.get('kind'), req.headers['content-type']?.split(';')[0], bytes);
      return send(res, 201, result);
    }
    if (!req.headers['content-type']?.startsWith('application/json')) bad('Cần gửi JSON');
    const bytes = await readBody(req, 256 * 1024);
    let body;
    try { body = JSON.parse(bytes.toString('utf8')); } catch { bad('JSON không hợp lệ'); }
    if (!body || typeof body !== 'object' || !Object.hasOwn(body, 'data') || Object.keys(body).some(k => k !== 'data')) bad('Sai định dạng callable');
    const data = decode(body.data);
    if (!data || typeof data !== 'object' || Array.isArray(data)) bad('Dữ liệu lệnh phải là object');
    const result = await handler({ data, auth: { uid: token.uid, token } });
    send(res, 200, { data: result });
  } catch (error) {
    if (error instanceof HttpsError) return send(res, codes[error.code] || 500, { error: error.toJSON() });
    // Log only error code, never request bodies (which may contain private dossiers).
    console.error('Business request failed', typeof error?.code === 'string' ? error.code : 'internal');
    send(res, 500, { error: { status: 'INTERNAL', message: 'Máy chủ chưa xử lý được. Thử lại hoặc liên hệ quản lý.' } });
  }
});
async function readBody(req, limit) {
  if (Number(req.headers['content-length']) > limit) bad('Tệp/yêu cầu vượt dung lượng cho phép', 'resource-exhausted');
  let length = 0; const chunks = [];
  for await (const chunk of req) {
    length += chunk.length;
    if (length > limit) bad('Tệp/yêu cầu vượt dung lượng cho phép', 'resource-exhausted');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}
server.requestTimeout = 30_000;
server.headersTimeout = 15_000;
server.listen(Number(process.env.PORT || 10000), '0.0.0.0', () => console.log('DriverGuard online API is listening'));
process.on('SIGTERM', () => { server.close(() => process.exit(0)); setTimeout(() => process.exit(1), 10_000).unref(); });
