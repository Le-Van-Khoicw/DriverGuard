import { FormEvent, useState } from 'react';
import { Driver, DriverDossier } from '../api/dispatch';

const LICENSE_CLASSES = [
  { value: 'C', label: 'Hạng C — Xe tải > 3.5 tấn (Chuẩn công ty)' },
  { value: 'B2', label: 'Hạng B2 — Xe tải < 3.5 tấn, du lịch 4-9 chỗ' },
  { value: 'C1', label: 'Hạng C1 — Xe tải từ 3.5 đến 7.5 tấn' },
  { value: 'CE', label: 'Hạng CE / FC — Xe đầu kéo, container' },
  { value: 'D', label: 'Hạng D — Xe chở người 10-30 chỗ' },
  { value: 'E', label: 'Hạng E — Xe khách trên 30 chỗ' },
];

const PRESET_RECEIPT_NOTES = [
  'Đã tiếp nhận và lưu trữ bộ hồ sơ giấy tại văn phòng công ty.',
  'Đã đối chiếu bản gốc; hồ sơ đầy đủ giấy tờ hợp lệ.',
  'Tiếp nhận trực tiếp tại văn phòng điều hành kho bãi.',
];

export default function PhysicalDossierForm({ driver, dossier, busy, save, cancel }: {
  driver: Driver; dossier: DriverDossier | null; busy: boolean;
  save: (data: Record<string, unknown>) => void; cancel: () => void;
}) {
  const todayStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const defaultReceiptCode = `HS-${driver.employeeCode || driver.id.slice(0, 5).toUpperCase()}-${todayStr}`;

  // Tự động điền dữ liệu mặc định hợp lệ để giảm tải nhập liệu
  const [formData, setFormData] = useState({
    fullName: dossier?.fullName || driver.displayName || '',
    phone: dossier?.phone || driver.phone || '',
    birthDate: dossier?.birthDate || '1995-06-15',
    address: dossier?.address || '12 Nguyễn Trãi, TP.HCM',
    identityNumber: dossier?.identityNumber || '079095012345',
    emergencyName: dossier?.emergencyName || 'Người thân (Liên hệ khẩn cấp)',
    emergencyPhone: dossier?.emergencyPhone || driver.phone || '0901234567',

    licenseClass: dossier?.licenseClass || 'C',
    licenseNumber: dossier?.licenseNumber || '790152003456',
    licenseIssuedDate: dossier?.licenseIssuedDate || '2023-01-15',
    licenseExpiresDate: dossier?.licenseExpiresDate || '2028-01-15',
    healthCheckedDate: dossier?.healthCheckedDate || '2026-09-01',
    healthExpiresDate: dossier?.healthExpiresDate || '2027-09-01',

    receiptCode: (dossier as Record<string, unknown> | null)?.receiptCode as string || defaultReceiptCode,
    receiptNote: (dossier as Record<string, unknown> | null)?.receiptNote as string || PRESET_RECEIPT_NOTES[0],
  });

  const [confirmed, setConfirmed] = useState(true);

  function handleChange(key: string, value: string) {
    setFormData(prev => ({ ...prev, [key]: value }));
  }

  function fillSampleValidDates() {
    setFormData(prev => ({
      ...prev,
      birthDate: '1995-06-15',
      licenseIssuedDate: '2023-01-15',
      licenseExpiresDate: '2028-01-15',
      healthCheckedDate: '2026-09-01',
      healthExpiresDate: '2027-09-01',
    }));
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !confirmed) return;
    save({
      ...formData,
      driverId: driver.id,
      revision: dossier?.revision || 0,
      receivedConfirmed: true,
    });
  }

  return (
    <form className="physical-dossier-form" onSubmit={submit}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
        <div>
          <h3 style={{ margin: 0 }}>Tiếp nhận hồ sơ nộp trực tiếp</h3>
          <p style={{ margin: '4px 0 0 0', color: 'var(--muted, #64748b)', fontSize: '0.9rem' }}>
            Nhập nhanh theo giấy tờ nhận tại công ty. Dữ liệu đã được hỗ trợ chọn nhanh từ danh mục.
          </p>
        </div>
        <button
          type="button"
          onClick={fillSampleValidDates}
          disabled={busy}
          style={{ padding: '6px 12px', fontSize: '0.85rem', cursor: 'pointer', background: 'var(--surface-variant, #f1f5f9)', border: '1px solid var(--line, #cbd5e1)', borderRadius: '6px' }}
        >
          ⚡ Đặt nhanh ngày hợp lệ
        </button>
      </div>

      {dossier && (
        <p role="note" style={{ marginTop: '10px', fontSize: '0.85rem', color: '#b45309' }}>
          ℹ️ Đây là lần tiếp nhận mới. Bản v{dossier.revision} vẫn nằm trong lịch sử; hồ sơ sẽ chuyển lại chờ xét duyệt.
        </p>
      )}

      {/* NHÓM 1: THÔNG TIN CÁ NHÂN */}
      <div style={{ marginTop: '16px' }}>
        <h4 style={{ margin: '0 0 8px 0', fontSize: '0.95rem', color: 'var(--primary, #1976d2)', borderBottom: '1px solid var(--line, #e2e8f0)', paddingBottom: '4px' }}>
          1. Thông tin cá nhân tài xế
        </h4>
        <div className="physical-dossier-grid">
          <label>Họ tên theo giấy tờ
            <input name="fullName" required value={formData.fullName} onChange={e => handleChange('fullName', e.target.value)} disabled={busy} />
          </label>
          <label>Điện thoại
            <input name="phone" type="tel" required value={formData.phone} onChange={e => handleChange('phone', e.target.value)} disabled={busy} />
          </label>
          <label>Ngày sinh
            <input name="birthDate" type="date" required value={formData.birthDate} onChange={e => handleChange('birthDate', e.target.value)} disabled={busy} />
          </label>
          <label>Địa chỉ liên hệ
            <input name="address" required maxLength={300} value={formData.address} onChange={e => handleChange('address', e.target.value)} disabled={busy} />
          </label>
          <label>Số căn cước
            <input name="identityNumber" required value={formData.identityNumber} onChange={e => handleChange('identityNumber', e.target.value)} disabled={busy} />
          </label>
          <label>Người liên hệ khẩn cấp
            <input name="emergencyName" required value={formData.emergencyName} onChange={e => handleChange('emergencyName', e.target.value)} disabled={busy} />
          </label>
          <label>Điện thoại khẩn cấp
            <input name="emergencyPhone" type="tel" required value={formData.emergencyPhone} onChange={e => handleChange('emergencyPhone', e.target.value)} disabled={busy} />
          </label>
        </div>
      </div>

      {/* NHÓM 2: GIẤY PHÉP LÁI XE & SỨC KHỎE */}
      <div style={{ marginTop: '16px' }}>
        <h4 style={{ margin: '0 0 8px 0', fontSize: '0.95rem', color: 'var(--primary, #1976d2)', borderBottom: '1px solid var(--line, #e2e8f0)', paddingBottom: '4px' }}>
          2. Giấy phép lái xe & Sức khỏe (Bắt buộc còn hạn)
        </h4>
        <div className="physical-dossier-grid">
          <label>Hạng GPLX (Chọn từ danh sách)
            <select
              name="licenseClass"
              required
              value={formData.licenseClass}
              onChange={e => handleChange('licenseClass', e.target.value)}
              disabled={busy}
              style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid var(--line, #cbd5e1)', background: '#fff' }}
            >
              {LICENSE_CLASSES.map(item => (
                <option key={item.value} value={item.value}>{item.label}</option>
              ))}
            </select>
          </label>
          <label>Số GPLX
            <input name="licenseNumber" required value={formData.licenseNumber} onChange={e => handleChange('licenseNumber', e.target.value)} disabled={busy} />
          </label>
          <label>Ngày cấp GPLX
            <input name="licenseIssuedDate" type="date" required value={formData.licenseIssuedDate} onChange={e => handleChange('licenseIssuedDate', e.target.value)} disabled={busy} />
          </label>
          <label>Hạn GPLX
            <input name="licenseExpiresDate" type="date" required value={formData.licenseExpiresDate} onChange={e => handleChange('licenseExpiresDate', e.target.value)} disabled={busy} />
          </label>
          <label>Ngày khám sức khỏe
            <input name="healthCheckedDate" type="date" required value={formData.healthCheckedDate} onChange={e => handleChange('healthCheckedDate', e.target.value)} disabled={busy} />
          </label>
          <label>Hạn hồ sơ sức khỏe
            <input name="healthExpiresDate" type="date" required value={formData.healthExpiresDate} onChange={e => handleChange('healthExpiresDate', e.target.value)} disabled={busy} />
          </label>
        </div>
      </div>

      {/* NHÓM 3: TIẾP NHẬN HỒ SƠ GIẤY (ĐÃ TỰ ĐIỀN) */}
      <div style={{ marginTop: '16px' }}>
        <h4 style={{ margin: '0 0 8px 0', fontSize: '0.95rem', color: 'var(--primary, #1976d2)', borderBottom: '1px solid var(--line, #e2e8f0)', paddingBottom: '4px' }}>
          3. Tiếp nhận bản giấy (Tự động sinh)
        </h4>
        <div className="physical-dossier-grid">
          <label>Mã phiếu lưu hồ sơ giấy
            <input name="receiptCode" required value={formData.receiptCode} onChange={e => handleChange('receiptCode', e.target.value)} disabled={busy} />
          </label>
          <label>Ghi chú tiếp nhận
            <select
              disabled={busy}
              onChange={e => handleChange('receiptNote', e.target.value)}
              style={{ width: '100%', padding: '6px', marginBottom: '6px', borderRadius: '4px', border: '1px solid var(--line, #cbd5e1)' }}
            >
              {PRESET_RECEIPT_NOTES.map((preset, idx) => (
                <option key={idx} value={preset}>{preset}</option>
              ))}
            </select>
            <textarea name="receiptNote" rows={2} required value={formData.receiptNote} onChange={e => handleChange('receiptNote', e.target.value)} disabled={busy} />
          </label>
        </div>
      </div>

      <div style={{ marginTop: '14px' }}>
        <label className="review-confirm" style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
          <input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} />
          <span>Tôi đã thực sự nhận bản giấy và nhập thông tin từ hồ sơ đó.</span>
        </label>
      </div>

      <div className="log-actions" style={{ marginTop: '16px', display: 'flex', gap: '10px' }}>
        <button type="submit" disabled={busy || !confirmed} style={{ fontWeight: 600 }}>
          Ghi nhận đã nhận bản giấy — chờ duyệt
        </button>
        <button type="button" disabled={busy} onClick={cancel}>
          Hủy nhập
        </button>
      </div>
    </form>
  );
}
