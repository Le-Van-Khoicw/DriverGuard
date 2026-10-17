import { useEffect, useRef, useState } from 'react';
import { doc, onSnapshot } from 'firebase/firestore';
import { getBlob, ref } from 'firebase/storage';
import { db, storage } from '../api/firebase';
import { Driver, DriverDossier, FleetVehicle, qualificationLabel } from '../api/dispatch';

const documentNames: Record<string, string> = { identity_front: 'Căn cước — mặt trước', identity_back: 'Căn cước — mặt sau', license_front: 'GPLX — mặt trước', license_back: 'GPLX — mặt sau', health: 'Giấy khám / xác nhận sức khỏe' };
export default function DriverProfileReview({ driver, admin, busy, error, review, vehicles }: {
  vehicles: FleetVehicle[]; driver: Driver; admin: boolean; busy: boolean; error: string;
  review: (data: Record<string, unknown>) => void;
}) {
  const [dossier, setDossier] = useState<DriverDossier | null>(null), [loading, setLoading] = useState(admin), [readError, setReadError] = useState('');
  const [previews, setPreviews] = useState<Record<string, { url: string; pdf: boolean }>>({});
  const previewUrls = useRef<string[]>([]);
  const [downloading, setDownloading] = useState('');
  const [vehicleIds, setVehicleIds] = useState<string[]>([]);
  const [verified, setVerified] = useState(false), [note, setNote] = useState('');
  useEffect(() => {
    if (!admin) return;
    return onSnapshot(doc(db, 'driverDossiers', driver.id), snapshot => {
      setDossier(snapshot.exists() ? snapshot.data() as DriverDossier : null); setLoading(false); setVerified(false); setVehicleIds([]); setNote('');
    }, e => { setReadError(e.message); setLoading(false); });
  }, [driver.id, admin]);
  useEffect(() => () => previewUrls.current.forEach(url => URL.revokeObjectURL(url)), []);
  async function preview(document: DriverDossier['documents'][number]) {
    setDownloading(document.kind); setReadError('');
    try { const blob = await getBlob(ref(storage, document.path), 5 * 1024 * 1024); const url = URL.createObjectURL(blob); previewUrls.current.push(url); setPreviews(v => ({ ...v, [document.kind]: { url, pdf: document.contentType === 'application/pdf' } })); }
    catch (e) { setReadError(e instanceof Error ? e.message : 'Không mở được tệp'); }
    finally { setDownloading(''); }
  }
  return <div className="driver-profile-review">
    <div className="profile-summary"><div className="profile-initial">{driver.displayName?.charAt(0).toUpperCase() || 'T'}</div><div><h3>{driver.displayName}</h3><p>{driver.employeeCode || 'Chưa cấp mã'} · {driver.email || 'Chưa có email'} · {driver.phone || 'Chưa có số điện thoại'}</p><span className="log-status">{qualificationLabel(driver)}</span></div></div>
    <p className="account-help">UID: {driver.id} · Trạng thái tài khoản: {driver.accountStatus}. Duyệt hồ sơ và kích hoạt tài khoản là hai bước riêng.</p>
    {!admin && <p>Điều phối được xem trạng thái và hạn hồ sơ. Ảnh giấy tờ, số định danh và thông tin sức khỏe chỉ dành cho chính tài xế và quản trị.</p>}
    {!admin && <p>GPLX: {driver.licenseClass || 'Chưa có'} · Hạn GPLX: {driver.licenseExpiresAt ? new Date(driver.licenseExpiresAt).toLocaleDateString('vi-VN') : '—'} · Hạn hồ sơ sức khỏe: {driver.healthExpiresAt ? new Date(driver.healthExpiresAt).toLocaleDateString('vi-VN') : '—'}</p>}
    {loading && <p>Đang đọc hồ sơ…</p>}{admin && !loading && !dossier && <p>Tài xế chưa nộp hồ sơ từ ứng dụng. Nhắc tài xế mở Hồ sơ nghề nghiệp để hoàn thiện.</p>}
    {(readError || error) && <p role="alert" className="log-error">{readError || error}</p>}
    {admin && dossier && <>
      <dl className="profile-details">{[['Họ tên theo giấy tờ', dossier.fullName], ['Điện thoại', dossier.phone], ['Ngày sinh', dossier.birthDate], ['Địa chỉ', dossier.address], ['Số căn cước', dossier.identityNumber], ['GPLX / hạng', `${dossier.licenseNumber} · ${dossier.licenseClass}`], ['GPLX: ngày cấp / hết hạn', `${dossier.licenseIssuedDate} → ${dossier.licenseExpiresDate}`], ['Sức khỏe: ngày khám / hạn quản lý', `${dossier.healthCheckedDate} → ${dossier.healthExpiresDate}`], ['Liên hệ khẩn cấp', `${dossier.emergencyName} · ${dossier.emergencyPhone}`], ['Lần nộp', `v${dossier.revision} · ${new Date(dossier.submittedAt).toLocaleString('vi-VN')}`]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      <h3>Tệp tài xế đã nộp</h3><div className="dossier-documents">{dossier.documents.map(d => <article key={d.path}><strong>{documentNames[d.kind] || d.kind}</strong><small>{d.name} · {(d.size / 1024).toFixed(0)} KB</small>{!previews[d.kind] && <button disabled={!!downloading} onClick={() => void preview(d)}>{downloading === d.kind ? 'Đang tải…' : 'Mở tệp riêng tư'}</button>}{previews[d.kind] && (previews[d.kind].pdf ? <a href={previews[d.kind].url} download={d.name}>Tải tệp PDF đã xác thực</a> : <a href={previews[d.kind].url} target="_blank" rel="noreferrer"><img src={previews[d.kind].url} alt={documentNames[d.kind]} /></a>)}</article>)}</div>
      {dossier.reviewNote && <p>Ghi chú quản lý: {dossier.reviewNote}</p>}
      {dossier.reviewedBy && <p className="account-help">Người duyệt: {dossier.reviewedBy} · {dossier.reviewedAt ? new Date(dossier.reviewedAt).toLocaleString('vi-VN') : ''}</p>}
      {dossier.status === 'submitted' && <form onSubmit={e => e.preventDefault()}><h3>Xe được xác nhận phù hợp hồ sơ</h3><p className="account-help">Đối chiếu hạng GPLX và hồ sơ xe thực tế. Không suy ra hạng GPLX từ số kg hàng chở.</p>{vehicles.map(v => <label className="review-confirm" key={v.id}><input type="checkbox" checked={vehicleIds.includes(v.id)} onChange={() => setVehicleIds(list => list.includes(v.id) ? list.filter(id => id !== v.id) : [...list, v.id])} />{v.plate}</label>)}{!vehicles.length && <p>Cần thêm xe công ty trước khi duyệt phù hợp xe.</p>}<label className="review-confirm"><input type="checkbox" checked={verified} onChange={e => setVerified(e.target.checked)} />Tôi đã đối chiếu giấy tờ, hạn và xác nhận điều kiện lái loại xe được công ty phân công.</label><label>Ghi chú đối chiếu / lý do yêu cầu bổ sung<textarea value={note} placeholder="VD: Đã đối chiếu bản gốc; hồ sơ phù hợp xe tải công ty phân công." onChange={e => setNote(e.target.value)} required /></label><div className="log-actions"><button disabled={busy || !verified || !vehicleIds.length || !note.trim()} onClick={() => review({ driverId: driver.id, revision: dossier.revision, status: 'approved', verified, vehicleIds, reason: note })}>Duyệt hồ sơ</button><button disabled={busy || !note.trim()} onClick={() => review({ driverId: driver.id, revision: dossier.revision, status: 'rejected', reason: note })}>Yêu cầu bổ sung</button></div></form>}
      <p className="account-help">Hệ thống lưu kết quả đối chiếu nội bộ, không tự kiểm định giấy tờ hay tra cứu điểm GPLX. Hồ sơ được duyệt vẫn phải được quản lý kiểm tra phù hợp với xe thực tế.</p>
    </>}
  </div>;
}
