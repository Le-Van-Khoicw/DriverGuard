import { documentKinds, requiredDocumentKinds, parseDocumentPath, documentMetadata } from './document-storage.js';
import { HttpsError } from 'firebase-functions/v2/https';

const invalid = message => { throw new HttpsError('invalid-argument', message); };
const required = (value, label, max = 300) => {
  if (typeof value !== 'string' || !value.trim() || value.length > max) invalid(`Thiếu hoặc sai ${label}`);
  return value.trim();
};
export const dossierDocuments = documentKinds;
export function endOfVietnamDate(value, label) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) invalid(`${label}: dùng ngày yyyy-MM-dd`);
  const day = new Date(`${value}T00:00:00Z`);
  if (!Number.isFinite(+day) || day.toISOString().slice(0, 10) !== value) invalid(`${label} không hợp lệ`);
  return Date.parse(`${value}T23:59:59.999+07:00`);
}
export async function validateDossier(p, uid, now, { requireUploads = true } = {}) {
  const fullName = required(p.fullName, 'họ tên', 120), phone = required(p.phone, 'điện thoại', 30);
  if (!/^\+?[0-9 ()-]{8,20}$/.test(phone)) invalid('Số điện thoại không hợp lệ');
  const birth = endOfVietnamDate(p.birthDate, 'Ngày sinh');
  if (birth >= now) invalid('Ngày sinh phải trước hôm nay');
  const licenseIssuedAt = endOfVietnamDate(p.licenseIssuedDate, 'Ngày cấp GPLX');
  const licenseExpiresAt = endOfVietnamDate(p.licenseExpiresDate, 'Hạn GPLX');
  const healthCheckedAt = endOfVietnamDate(p.healthCheckedDate, 'Ngày khám sức khỏe');
  const healthExpiresAt = endOfVietnamDate(p.healthExpiresDate, 'Hạn hồ sơ sức khỏe');
  if (licenseIssuedAt >= licenseExpiresAt || healthCheckedAt >= healthExpiresAt || licenseExpiresAt <= now || healthExpiresAt <= now) invalid('Kiểm tra lại ngày cấp/khám và hạn còn hiệu lực');
  if (licenseIssuedAt - 86_400_000 + 1 > now || healthCheckedAt - 86_400_000 + 1 > now) invalid('Ngày cấp GPLX / ngày khám không được ở tương lai');
  // Only the admin-only physical-receipt handler disables uploads; never trust a client flag.
  const docs = requireUploads ? p.documents : [];
  if (!Array.isArray(docs) || docs.length > dossierDocuments.length || docs.some(d => !d || !dossierDocuments.includes(d.kind)) ||
      new Set(docs.map(d => d.kind)).size !== docs.length || (requireUploads && requiredDocumentKinds.some(kind => !docs.some(d => d.kind === kind)))) invalid('Cần hai mặt GPLX; giấy tờ bổ sung không được trùng loại');
  const documents = await Promise.all(docs.map(async document => {
    const kind = document.kind;
    const path = required(document?.path, `tệp ${kind}`, 500);
    const parsed = parseDocumentPath(path);
    if (parsed.uid !== uid || parsed.kind !== kind) invalid('Đường dẫn giấy tờ không thuộc tài khoản');
    const metadata = await documentMetadata(path);
    return { kind, path, name: required(document.name, 'tên tệp', 180), contentType: metadata.contentType, size: Number(metadata.size) };
  }));
  const emergencyPhone = required(p.emergencyPhone, 'điện thoại khẩn cấp', 30);
  if (!/^\+?[0-9 ()-]{8,20}$/.test(emergencyPhone)) invalid('Số điện thoại khẩn cấp không hợp lệ');
  return {
    fullName, phone, birthDate: p.birthDate, address: required(p.address, 'địa chỉ'),
    identityNumber: required(p.identityNumber, 'số căn cước', 30), licenseNumber: required(p.licenseNumber, 'số GPLX', 50),
    licenseClass: required(p.licenseClass, 'hạng GPLX (ghi đúng trên giấy)', 30),
    licenseIssuedDate: p.licenseIssuedDate, licenseExpiresDate: p.licenseExpiresDate,
    healthCheckedDate: p.healthCheckedDate, healthExpiresDate: p.healthExpiresDate,
    licenseExpiresAt, healthExpiresAt, emergencyName: required(p.emergencyName, 'liên hệ khẩn cấp', 120),
    emergencyPhone, documents,
  };
}
export function requireApprovedDossier(driver, until, vehicleId) {
  const name = driver?.displayName || 'Tài xế được chọn';
  if (!driver?.qualificationStatus) throw new HttpsError('failed-precondition', `${name} chưa có hồ sơ nghề nghiệp. Tài xế nộp trên app hoặc quản lý ghi nhận hồ sơ giấy trực tiếp, rồi xét duyệt.`);
  if (driver.qualificationStatus === 'submitted') throw new HttpsError('failed-precondition', `Hồ sơ của ${name} đang chờ duyệt. Mở Tài xế → Xem/duyệt hồ sơ để xử lý.`);
  if (driver.qualificationStatus !== 'approved') throw new HttpsError('failed-precondition', `Hồ sơ của ${name} cần bổ sung và duyệt lại trước khi phân công.`);
  if (!(driver.licenseExpiresAt > until) || !(driver.healthExpiresAt > until)) throw new HttpsError('failed-precondition', 'Giấy phép/hồ sơ sức khỏe hết hạn trước khi kết thúc chuyến');
  if (vehicleId && !driver.approvedVehicleIds?.includes(vehicleId)) throw new HttpsError('failed-precondition', 'Quản lý chưa xác nhận hồ sơ tài xế phù hợp với xe này');
}
