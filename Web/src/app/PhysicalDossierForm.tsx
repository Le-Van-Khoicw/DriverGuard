import { FormEvent, useState } from 'react';
import { Driver, DriverDossier } from '../api/dispatch';

const mainFields = [
  ['fullName', 'Họ tên theo giấy tờ', 'text', 'VD: Nguyễn Văn Nam'],
  ['phone', 'Điện thoại', 'tel', 'VD: 0901234567'],
  ['licenseNumber', 'Số GPLX', 'text', 'Nhập theo giấy phép lái xe'],
  ['licenseExpiresDate', 'GPLX có giá trị đến', 'date', ''],
  ['healthExpiresDate', 'Hạn hồ sơ sức khỏe', 'date', ''],
];
const extraFields = [
  ['birthDate', 'Ngày sinh', 'date', ''], ['address', 'Địa chỉ liên hệ', 'text', 'VD: 12 Nguyễn Trãi, TP.HCM'],
  ['identityNumber', 'Số căn cước', 'text', 'Nhập theo giấy tờ'], ['licenseIssuedDate', 'Ngày cấp GPLX', 'date', ''],
  ['healthCheckedDate', 'Ngày khám sức khỏe', 'date', ''], ['emergencyName', 'Người liên hệ khẩn cấp', 'text', 'VD: Nguyễn Thị An — người thân'],
  ['emergencyPhone', 'Điện thoại khẩn cấp', 'tel', 'VD: 0912345678'],
];
const licenseClasses = ['B', 'B2', 'C1', 'C', 'BE', 'C1E', 'CE', 'FC', 'D1', 'D2', 'D', 'E', 'D1E', 'D2E', 'DE', 'FB2', 'FD', 'FE'];

export default function PhysicalDossierForm({ driver, dossier, busy, save, cancel }: {
  driver: Driver; dossier: DriverDossier | null; busy: boolean;
  save: (data: Record<string, unknown>) => void; cancel: () => void;
}) {
  // Pre-fill only real saved data. Examples belong in placeholders, never in submitted values.
  const [fields, setFields] = useState<Record<string, string>>(() => Object.fromEntries(
    [...mainFields, ...extraFields, ['licenseClass'], ['receiptNote']].map(([key]) => [key,
      String(dossier?.[key as keyof DriverDossier] ?? (key === 'fullName' ? driver.displayName : key === 'phone' ? driver.phone : '') ?? '')])
  ));
  const [confirmed, setConfirmed] = useState(false);
  const change = (key: string, value: string) => setFields(old => ({ ...old, [key]: value }));
  function input([key, label, type, example]: string[], required: boolean) {
    return <label key={key}>{label}{required ? ' *' : ''}<input name={key} type={type} required={required}
      value={fields[key] || ''} onChange={event => change(key, event.target.value)} placeholder={example}
      maxLength={key === 'fullName' || key === 'emergencyName' ? 120 : key === 'address' ? 300 : 50} disabled={busy} /></label>;
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!busy && confirmed) save({ ...fields, driverId: driver.id, revision: dossier?.revision || 0, receivedConfirmed: true });
  }
  return <form className="physical-dossier-form" onSubmit={submit}>
    <h3>Tiếp nhận hồ sơ nộp trực tiếp</h3>
    <p>Nhập thông tin chính theo bản giấy. Mã tiếp nhận được hệ thống cấp khi lưu; hồ sơ vẫn cần quản lý xét duyệt.</p>
    {dossier && <p className="account-help">Lần tiếp nhận mới sẽ chuyển hồ sơ về chờ duyệt; bản v{dossier.revision} vẫn được giữ trong lịch sử.</p>}
    <div className="physical-dossier-grid">
      {mainFields.slice(0, 3).map(field => input(field, true))}
      <label>Hạng GPLX *<select required value={fields.licenseClass || ''} onChange={e => change('licenseClass', e.target.value)} disabled={busy}>
        <option value="">Chọn hạng ghi trên GPLX</option>
        {[...new Set([...licenseClasses, ...(fields.licenseClass ? [fields.licenseClass] : [])])].map(value => <option key={value} value={value}>Hạng {value}</option>)}
      </select></label>
      {mainFields.slice(3).map(field => input(field, true))}
    </div>
    <p className="account-help">Hồ sơ này dùng cho tài xế ô tô / xe tải: nhập hạn ghi trên GPLX và hồ sơ sức khỏe thực tế, không tự suy đoán từ ngày cấp.</p>
    <details><summary>Thông tin bổ sung (không bắt buộc)</summary>
      <div className="physical-dossier-grid">{extraFields.map(field => input(field, false))}
        <label>Ghi chú / nơi lưu bản giấy
          <select aria-label="Chọn nhanh nơi lưu bản giấy" value={['Lưu tại văn phòng công ty.', 'Lưu tại kho / bãi điều hành.'].includes(fields.receiptNote) ? fields.receiptNote : ''} onChange={e => change('receiptNote', e.target.value)} disabled={busy}>
            <option value="">Khác / tự ghi bên dưới</option><option>Lưu tại văn phòng công ty.</option><option>Lưu tại kho / bãi điều hành.</option>
          </select>
          <textarea rows={2} maxLength={1000} value={fields.receiptNote || ''} onChange={e => change('receiptNote', e.target.value)} placeholder="VD: Tủ hồ sơ tài xế, ngăn số 2" disabled={busy} />
        </label>
      </div>
    </details>
    <p className="account-help">Mã tiếp nhận: tự sinh khi lưu (VD: HS-000001).</p>
    <label className="review-confirm"><input type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)} disabled={busy} />Tôi đã thực sự nhận và kiểm tra thông tin trên bản giấy.</label>
    <div className="log-actions"><button disabled={busy || !confirmed}>Ghi nhận bản giấy — chờ duyệt</button><button type="button" onClick={cancel} disabled={busy}>Hủy</button></div>
  </form>;
}
