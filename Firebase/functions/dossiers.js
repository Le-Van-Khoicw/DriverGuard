import { getStorage } from 'firebase-admin/storage';
import { HttpsError } from 'firebase-functions/v2/https';

const invalid = message => { throw new HttpsError('invalid-argument', message); };
const required = (value, label, max = 300) => {
  if (typeof value !== 'string' || !value.trim() || value.length > max) invalid(`Thiếu hoặc sai ${label}`);
  return value.trim();
};
export const dossierDocuments = ['identity_front', 'identity_back', 'license_front', 'license_back', 'health'];
export function endOfVietnamDate(value, label) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) invalid(`${label}: dùng ngày yyyy-MM-dd`);
  const day = new Date(`${value}T00:00:00Z`);
  if (!Number.isFinite(+day) || day.toISOString().slice(0, 10) !== value) invalid(`${label} không hợp lệ`);
  return Date.parse(`${value}T23:59:59.999+07:00`);
}
export async function validateDossier(p, uid, now) {
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
  const docs = p.documents;
  if (!Array.isArray(docs) || docs.length !== dossierDocuments.length || new Set(docs.map(d => d.kind)).size !== docs.length) invalid('Cần đủ 5 tệp giấy tờ, không trùng loại');
  const documents = await Promise.all(dossierDocuments.map(async kind => {
    const document = docs.find(d => d.kind === kind);
    const path = required(document?.path, `tệp ${kind}`, 500);
    if (!path.startsWith(`driver-documents/${uid}/${kind}/`) || path.split('/').length !== 4 || path.includes('..')) invalid('Đường dẫn giấy tờ không thuộc tài khoản');
    const bucket = getStorage().bucket(process.env.FUNCTIONS_EMULATOR === 'true' ? 'demo-driverguard.appspot.com' : undefined);
    let metadata;
    try { [metadata] = await bucket.file(path).getMetadata(); } catch { invalid(`Chưa tải xong tệp ${kind}; chọn và tải lại`); }
    if (!['image/jpeg', 'image/png', 'application/pdf'].includes(metadata.contentType) || Number(metadata.size) > 5 * 1024 * 1024) invalid('Chỉ nhận JPG, PNG, PDF tối đa 5 MB/tệp');
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
  if (driver?.qualificationStatus !== 'approved') throw new HttpsError('failed-precondition', 'Hồ sơ tài xế chưa được quản lý duyệt');
  if (!(driver.licenseExpiresAt > until) || !(driver.healthExpiresAt > until)) throw new HttpsError('failed-precondition', 'Giấy phép/hồ sơ sức khỏe hết hạn trước khi kết thúc chuyến');
  if (vehicleId && !driver.approvedVehicleIds?.includes(vehicleId)) throw new HttpsError('failed-precondition', 'Quản lý chưa xác nhận hồ sơ tài xế phù hợp với xe này');
}
