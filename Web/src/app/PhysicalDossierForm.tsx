import { FormEvent, useState } from 'react';
import { Driver, DriverDossier } from '../api/dispatch';

const fields = [
  ['fullName', 'Họ tên theo giấy tờ', 'text', 'VD: Nguyễn Văn Nam'],
  ['phone', 'Điện thoại', 'tel', 'VD: 0901234567'],
  ['birthDate', 'Ngày sinh', 'date', ''],
  ['address', 'Địa chỉ liên hệ', 'text', 'VD: 12 Nguyễn Trãi, TP.HCM'],
  ['identityNumber', 'Số căn cước', 'text', 'Đối chiếu trên bản giấy'],
  ['licenseNumber', 'Số GPLX', 'text', 'Ghi đúng trên GPLX'],
  ['licenseClass', 'Hạng GPLX', 'text', 'VD: C, CE; theo giấy thực tế'],
  ['licenseIssuedDate', 'Ngày cấp GPLX', 'date', ''],
  ['licenseExpiresDate', 'Hạn GPLX', 'date', ''],
  ['healthCheckedDate', 'Ngày khám sức khỏe', 'date', ''],
  ['healthExpiresDate', 'Hạn hồ sơ sức khỏe theo giấy / quy trình công ty', 'date', ''],
  ['emergencyName', 'Người liên hệ khẩn cấp', 'text', 'VD: Nguyễn Thị An — vợ'],
  ['emergencyPhone', 'Điện thoại khẩn cấp', 'tel', 'VD: 0912345678'],
] as const;

export default function PhysicalDossierForm({ driver, dossier, busy, save, cancel }: {
  driver: Driver; dossier: DriverDossier | null; busy: boolean;
  save: (data: Record<string, unknown>) => void; cancel: () => void;
}) {
  const [confirmed, setConfirmed] = useState(false);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !confirmed) return;
    save({ ...Object.fromEntries(new FormData(event.currentTarget)), driverId: driver.id,
      revision: dossier?.revision || 0, receivedConfirmed: true });
  }
  return <form className="physical-dossier-form" onSubmit={submit}>
    <h3>Tiếp nhận hồ sơ nộp trực tiếp</h3>
    <p>Nhập theo giấy tờ đã nhận tại công ty. Không yêu cầu tải ảnh. Sau khi ghi nhận, hồ sơ ở trạng thái chờ duyệt; quản lý tiếp tục đối chiếu và chọn xe phù hợp.</p>
    {dossier && <p role="note">Đây là lần tiếp nhận mới. Bản v{dossier.revision} vẫn nằm trong lịch sử; nếu đang được duyệt, hồ sơ sẽ chuyển lại chờ xét duyệt.</p>}
    <div className="physical-dossier-grid">{fields.map(([key, label, type, placeholder]) => {
      const saved = dossier?.[key];
      const fallback = key === 'fullName' ? driver.displayName : key === 'phone' ? driver.phone : '';
      return <label key={key}>{label}<input name={key} type={type} required maxLength={key === 'address' ? 300 : 120}
        defaultValue={typeof saved === 'string' ? saved : fallback} placeholder={placeholder} disabled={busy} /></label>;
    })}
      <label>Mã phiếu / mã lưu hồ sơ giấy<input name="receiptCode" required maxLength={100} placeholder="VD: HS-TX001-20261022" disabled={busy} /></label>
      <label>Ghi chú tiếp nhận / nơi lưu bản giấy<textarea name="receiptNote" required maxLength={1000} placeholder="VD: Đã nhận bộ hồ sơ tại văn phòng, lưu tủ A/ngăn 2." disabled={busy} /></label>
    </div>
    <label className="review-confirm"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} />Tôi đã thực sự nhận bản giấy và nhập thông tin từ hồ sơ đó.</label>
    <p className="account-help">Người ghi nhận và thời điểm tiếp nhận được máy chủ lưu theo tài khoản quản lý hiện tại.</p>
    <div className="log-actions"><button type="submit" disabled={busy || !confirmed}>Ghi nhận đã nhận bản giấy — chờ duyệt</button><button type="button" disabled={busy} onClick={cancel}>Hủy nhập</button></div>
  </form>;
}
