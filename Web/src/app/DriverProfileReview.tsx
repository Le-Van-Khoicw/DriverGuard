import { useEffect, useRef, useState } from 'react';
import { doc, onSnapshot } from 'firebase/firestore';
import { db } from '../api/firebase';
import { readDocument } from '../api/documentFiles';
import { Driver, DriverDossier, FleetVehicle, qualificationLabel } from '../api/dispatch';
import PhysicalDossierForm from './PhysicalDossierForm';

const documentNames: Record<string, string> = {
  identity_front: 'Căn cước — mặt trước',
  identity_back: 'Căn cước — mặt sau',
  license_front: 'GPLX — mặt trước',
  license_back: 'GPLX — mặt sau',
  health: 'Giấy khám / xác nhận sức khỏe'
};

export default function DriverProfileReview({ driver, admin, busy, error, review, recordPhysical, vehicles }: {
  vehicles: FleetVehicle[]; driver: Driver; admin: boolean; busy: boolean; error: string;
  review: (data: Record<string, unknown>) => void;
  recordPhysical: (data: Record<string, unknown>) => void;
}) {
  const [dossier, setDossier] = useState<DriverDossier | null>(null);
  const [loading, setLoading] = useState(admin);
  const [readError, setReadError] = useState('');
  const [previews, setPreviews] = useState<Record<string, { url: string; pdf: boolean }>>({});
  const previewUrls = useRef<string[]>([]);
  const previewVersion = useRef(0);
  const [downloading, setDownloading] = useState('');
  const [vehicleIds, setVehicleIds] = useState<string[]>([]);
  const [verified, setVerified] = useState(true);
  const [note, setNote] = useState('Đã đối chiếu bản gốc; hồ sơ phù hợp xe công ty phân công.');
  const [recording, setRecording] = useState(false);

  useEffect(() => { setRecording(false); }, [dossier?.revision]);

  useEffect(() => {
    if (!admin) return;
    setLoading(true); setDossier(null); setReadError('');
    return onSnapshot(doc(db, 'driverDossiers', driver.id), snapshot => {
      const data = snapshot.exists() ? (snapshot.data() as DriverDossier) : null;
      setDossier(data);
      setLoading(false);
      // Tự động chọn sẵn xe công ty và xác nhận để nút Duyệt sáng ngay
      setVerified(true);
      setNote('Đã đối chiếu bản gốc; hồ sơ phù hợp xe công ty phân công.');
      if (vehicles.length > 0) {
        setVehicleIds(vehicles.map(v => v.id));
      }
    }, e => { setReadError(e.message); setLoading(false); });
  }, [driver.id, admin, vehicles]);

  useEffect(() => {
    previewVersion.current += 1;
    previewUrls.current.forEach(url => URL.revokeObjectURL(url));
    previewUrls.current = [];
    setPreviews({});
    setDownloading('');
    return () => {
      previewVersion.current += 1;
      previewUrls.current.forEach(url => URL.revokeObjectURL(url));
      previewUrls.current = [];
    };
  }, [driver.id, dossier?.revision]);

  async function preview(document: DriverDossier['documents'][number]) {
    const version = previewVersion.current;
    setDownloading(document.kind);
    setReadError('');
    try {
      const blob = await readDocument(document.path);
      if (version !== previewVersion.current) return;
      const url = URL.createObjectURL(blob);
      previewUrls.current.push(url);
      setPreviews(v => ({ ...v, [document.kind]: { url, pdf: document.contentType === 'application/pdf' } }));
    } catch (e) {
      if (version === previewVersion.current) setReadError(e instanceof Error ? e.message : 'Không mở được tệp');
    } finally {
      if (version === previewVersion.current) setDownloading('');
    }
  }

  const isRejected = dossier?.status === 'rejected' || driver.qualificationStatus === 'rejected';
  const isApproved = dossier?.status === 'approved' || driver.qualificationStatus === 'approved';

  return (
    <div className="driver-profile-review">
      <div className="profile-summary">
        <div className="profile-initial">{driver.displayName?.charAt(0).toUpperCase() || 'T'}</div>
        <div>
          <h3>{driver.displayName}</h3>
          <p>{driver.employeeCode || 'Chưa cấp mã'} · {driver.email || 'Chưa có email'} · {driver.phone || 'Chưa có số điện thoại'}</p>
          <span className={`log-status ${isApproved ? 'log-approved' : isRejected ? 'log-rejected' : 'log-pending'}`}>
            {qualificationLabel(driver)}
          </span>
        </div>
      </div>

      <p className="account-help">
        UID: {driver.id} · Trạng thái tài khoản: <strong>{driver.accountStatus}</strong>.
      </p>

      {!admin && (
        <>
          <p>Điều phối được xem trạng thái và hạn hồ sơ. Ảnh giấy tờ, số định danh và thông tin sức khỏe chỉ dành cho chính tài xế và quản trị.</p>
          <p>GPLX: {driver.licenseClass || 'Chưa có'} · Hạn GPLX: {driver.licenseExpiresAt ? new Date(driver.licenseExpiresAt).toLocaleDateString('vi-VN') : '—'} · Hạn hồ sơ sức khỏe: {driver.healthExpiresAt ? new Date(driver.healthExpiresAt).toLocaleDateString('vi-VN') : '—'}</p>
        </>
      )}

      {loading && <p>Đang đọc hồ sơ…</p>}
      {admin && !loading && !dossier && (
        <p>Chưa có hồ sơ của {driver.displayName} để xét duyệt. Bấm nút bên dưới để ghi nhận hồ sơ bản giấy.</p>
      )}

      {(readError || error) && <p role="alert" className="log-error">{readError || error}</p>}

      {/* CẢNH BÁO KHI BỊ REJECTED */}
      {admin && isRejected && !recording && (
        <div style={{ margin: '14px 0', padding: '12px 16px', background: '#fef2f2', border: '1px solid #f87171', borderRadius: '8px', color: '#991b1b' }}>
          <strong>⚠️ Hồ sơ đang bị từ chối (Cần bổ sung và duyệt lại)</strong>
          <p style={{ margin: '4px 0' }}>Lý do trước đó: {dossier?.reviewNote || 'Chưa có ghi chú'}</p>
          <p style={{ margin: '4px 0', fontSize: '0.85rem' }}>👉 Để cho tài xế nhận chuyến, hãy bấm <strong>"Tiếp nhận lại để duyệt"</strong> bên dưới rồi bấm <strong>"Duyệt hồ sơ (Đạt)"</strong>.</p>
          <button
            type="button"
            style={{ marginTop: '8px', padding: '6px 14px', background: '#dc2626', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 }}
            onClick={() => setRecording(true)}
          >
            🔄 Tiếp nhận lại để duyệt ngay
          </button>
        </div>
      )}

      {admin && !loading && !readError && (
        <div className="log-actions" style={{ marginTop: '10px' }}>
          <button type="button" disabled={busy} onClick={() => setRecording(v => !v)}>
            {recording ? 'Đóng tiếp nhận bản giấy' : '📝 Ghi nhận / Cập nhật hồ sơ bản giấy'}
          </button>
        </div>
      )}

      {admin && recording && (
        <PhysicalDossierForm
          key={`${driver.id}_${dossier?.revision || 0}`}
          driver={driver}
          dossier={dossier}
          busy={busy}
          save={recordPhysical}
          cancel={() => setRecording(false)}
        />
      )}

      {admin && dossier && (
        <>
          <p style={{ marginTop: '14px' }}>
            <strong>Hình thức nộp:</strong> {dossier.submissionMethod === 'in_person' ? 'Nộp trực tiếp tại công ty (bản giấy)' : 'Nộp online qua app'}
          </p>

          {dossier.submissionMethod === 'in_person' && (
            <div className="driver-workflow-note">
              <strong>Đã ghi nhận nhận bản giấy</strong>
              <p>Mã lưu: {dossier.receiptCode} · Người nhận: {dossier.receivedByName || dossier.receivedBy} · {dossier.receivedAt ? new Date(dossier.receivedAt).toLocaleString('vi-VN') : '—'}</p>
              <p>{dossier.receiptNote}</p>
            </div>
          )}

          <dl className="profile-details">
            {[
              ['Họ tên theo giấy tờ', dossier.fullName],
              ['Điện thoại', dossier.phone],
              ['Ngày sinh', dossier.birthDate],
              ['Địa chỉ', dossier.address],
              ['Số căn cước', dossier.identityNumber],
              ['GPLX / hạng', `${dossier.licenseNumber} · Hạng ${dossier.licenseClass}`],
              ['GPLX: ngày cấp / hết hạn', `${dossier.licenseIssuedDate || 'Chưa bổ sung ngày cấp'} → ${dossier.licenseExpiresDate}`],
              ['Sức khỏe: ngày khám / hạn quản lý', `${dossier.healthCheckedDate || 'Chưa bổ sung ngày khám'} → ${dossier.healthExpiresDate}`],
              ['Liên hệ khẩn cấp', [dossier.emergencyName, dossier.emergencyPhone].filter(Boolean).join(' · ')],
              ['Lần nộp', `v${dossier.revision} · ${new Date(dossier.submittedAt).toLocaleString('vi-VN')}`]
            ].map(([label, value]) => (
              <div key={label}><dt>{label}</dt><dd>{value || 'Chưa bổ sung'}</dd></div>
            ))}
          </dl>

          <h3>{dossier.submissionMethod === 'in_person' ? 'Tài liệu tiếp nhận trực tiếp' : 'Tệp tài xế đã nộp'}</h3>
          {dossier.submissionMethod === 'in_person' && (
            <p className="account-help">Giấy tờ được lưu tại công ty theo phiếu tiếp nhận. Quản lý đối chiếu trực tiếp bộ hồ sơ.</p>
          )}

          <div className="dossier-documents">
            {dossier.documents.map(d => (
              <article key={d.path}>
                <strong>{documentNames[d.kind] || d.kind}</strong>
                <small>{d.name} · {(d.size / 1024).toFixed(0)} KB</small>
                {!previews[d.kind] && (
                  <button disabled={!!downloading} onClick={() => void preview(d)}>
                    {downloading === d.kind ? 'Đang tải…' : 'Mở tệp'}
                  </button>
                )}
                {previews[d.kind] && (
                  previews[d.kind].pdf ? (
                    <a href={previews[d.kind].url} download={d.name}>Tải tệp PDF</a>
                  ) : (
                    <a href={previews[d.kind].url} target="_blank" rel="noreferrer">
                      <img src={previews[d.kind].url} alt={documentNames[d.kind]} />
                    </a>
                  )
                )}
              </article>
            ))}
          </div>

          {dossier.reviewNote && <p><strong>Ghi chú xét duyệt:</strong> {dossier.reviewNote}</p>}
          {dossier.reviewedBy && (
            <p className="account-help">Người duyệt: {dossier.reviewedBy} · {dossier.reviewedAt ? new Date(dossier.reviewedAt).toLocaleString('vi-VN') : ''}</p>
          )}

          {/* FORM XÉT DUYỆT (CHỈ HIỆN KHI SUBMITTED) */}
          {dossier.status === 'submitted' && !recording && (
            <form onSubmit={e => e.preventDefault()} style={{ marginTop: '16px', padding: '16px', background: 'var(--surface-variant, #f8fafc)', border: '1px solid var(--line, #e2e8f0)', borderRadius: '10px' }}>
              <h3 style={{ margin: '0 0 8px 0', color: '#16a34a' }}>✅ Xét duyệt hồ sơ & Phân quyền lái xe</h3>
              <p className="account-help" style={{ margin: '0 0 12px 0' }}>
                Chọn các loại xe công ty mà tài xế này được phép điều khiển:
              </p>

              <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginBottom: '12px' }}>
                {vehicles.map(v => (
                  <label className="review-confirm" key={v.id} style={{ padding: '6px 12px', background: '#fff', border: '1px solid var(--line, #cbd5e1)', borderRadius: '6px', cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={vehicleIds.includes(v.id)}
                      onChange={() => setVehicleIds(list => list.includes(v.id) ? list.filter(id => id !== v.id) : [...list, v.id])}
                    />
                    <span style={{ fontWeight: 600 }}>{v.plate}</span> ({v.capacityKg} kg)
                  </label>
                ))}
              </div>

              {!vehicles.length && <p style={{ color: '#dc2626' }}>Cần thêm xe công ty trước khi duyệt.</p>}

              <label className="review-confirm" style={{ display: 'flex', alignItems: 'center', gap: '8px', margin: '12px 0', cursor: 'pointer' }}>
                <input type="checkbox" checked={verified} onChange={e => setVerified(e.target.checked)} />
                <span>Tôi đã đối chiếu giấy tờ và xác nhận tài xế đủ điều kiện lái xe.</span>
              </label>

              <label style={{ display: 'block', margin: '10px 0' }}>
                Ghi chú đối chiếu (Chọn nhanh từ danh sách hoặc tự ghi)
                <select
                  disabled={busy}
                  onChange={e => setNote(e.target.value)}
                  style={{ width: '100%', padding: '6px', marginBottom: '6px', borderRadius: '4px', border: '1px solid var(--line, #cbd5e1)' }}
                >
                  <option value="Đã đối chiếu bản gốc; hồ sơ phù hợp xe công ty phân công.">
                    Đã đối chiếu bản gốc; hồ sơ phù hợp xe công ty phân công. (Chuẩn)
                  </option>
                  <option value="Hồ sơ hợp lệ, đủ điều kiện lái xe và nhận chuyến.">
                    Hồ sơ hợp lệ, đủ điều kiện lái xe và nhận chuyến.
                  </option>
                  <option value="Giấy tờ đầy đủ và còn thời hạn theo quy định.">
                    Giấy tờ đầy đủ và còn thời hạn theo quy định.
                  </option>
                </select>
                <textarea
                  value={note}
                  placeholder="VD: Đã đối chiếu bản gốc; hồ sơ phù hợp xe tải công ty phân công."
                  onChange={e => setNote(e.target.value)}
                  required
                  rows={2}
                  style={{ width: '100%', marginTop: '4px' }}
                />
              </label>

              <div className="log-actions" style={{ marginTop: '14px', display: 'flex', gap: '12px' }}>
                <button
                  type="button"
                  disabled={busy || !verified || !vehicleIds.length || !note.trim()}
                  onClick={() => review({ driverId: driver.id, revision: dossier.revision, status: 'approved', verified, vehicleIds, reason: note })}
                  style={{ background: '#16a34a', color: '#fff', fontWeight: 600, padding: '10px 20px', border: 'none', borderRadius: '6px', cursor: 'pointer' }}
                >
                  👍 DUYỆT HỒ SƠ (CHO PHÉP CHẠY XE)
                </button>
                <button
                  type="button"
                  disabled={busy || !note.trim()}
                  onClick={() => review({ driverId: driver.id, revision: dossier.revision, status: 'rejected', reason: note })}
                  style={{ background: 'transparent', color: '#dc2626', border: '1px solid #dc2626', padding: '10px 16px', borderRadius: '6px', cursor: 'pointer' }}
                >
                  ❌ Yêu cầu bổ sung (Từ chối)
                </button>
              </div>
            </form>
          )}

          <p className="account-help" style={{ marginTop: '14px' }}>
            Hồ sơ được duyệt sẽ kích hoạt quyền nhận chuyến và điều phối xe cho tài xế.
          </p>
        </>
      )}
    </div>
  );
}
