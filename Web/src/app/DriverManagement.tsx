import { useEffect, useState } from 'react';
import { Driver, qualificationLabel, Trip } from '../api/dispatch';

export default function DriverManagement({ drivers, trips, admin, busy, search, openProfile, saveAccount }: {
  drivers: Driver[]; trips: Trip[]; admin: boolean; busy: boolean; search: string;
  openProfile: (uid: string) => void; saveAccount: (data: Record<string, unknown>) => void;
}) {
  return <>
    <div className="driver-workflow-note"><strong>Tài khoản và hồ sơ nghề nghiệp là hai phần riêng</strong><p>“Hoạt động” nghĩa là tài khoản đã mở. Để giao chuyến, tài xế cần nộp online trên app hoặc nộp bản giấy được quản lý ghi nhận, sau đó được duyệt hồ sơ, xác nhận xe phù hợp.</p><ol><li>Tài xế nộp online trên app hoặc quản lý ghi nhận đã nhận bản giấy tại công ty.</li><li>Quản lý bấm <strong>Xem/duyệt hồ sơ</strong> để đối chiếu giấy tờ.</li><li>Kích hoạt tài khoản nếu cần, rồi giao chuyến.</li></ol></div>
    <div className="log-table"><table><thead><tr><th>Tài xế</th><th>Liên hệ</th><th>Tài khoản</th><th>Hồ sơ nghề nghiệp</th><th>Quản lý tài khoản</th></tr></thead><tbody>{drivers.filter(d => JSON.stringify(d).toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi'))).map(d => <tr key={d.id}><td><strong>{d.displayName}</strong><small>{d.employeeCode || 'Chưa cấp mã'}</small></td><td>{d.phone}<small>{d.email}</small></td><td><span className="log-status">{({ active: 'Hoạt động', pending: 'Chờ kích hoạt', locked: 'Đã khóa', rejected: 'Từ chối' } as Record<string, string>)[d.accountStatus] || d.accountStatus}</span><small>{trips.filter(t => t.driverId === d.id && !['completed', 'completed_issues', 'cancelled'].includes(t.status)).length} chuyến chưa kết thúc</small></td><td><strong>{qualificationLabel(d)}</strong><small>{d.licenseClass && `GPLX ${d.licenseClass}`}</small><button onClick={() => openProfile(d.id)}>{admin ? 'Xem/duyệt hồ sơ' : 'Xem trạng thái hồ sơ'}</button></td><td>{admin && <DriverAccountForm key={d.id} driver={d} busy={busy} saveAccount={saveAccount} />}</td></tr>)}</tbody></table></div>
  </>;
}
function DriverAccountForm({ driver, busy, saveAccount }: { driver: Driver; busy: boolean; saveAccount: (data: Record<string, unknown>) => void }) {
  const [status, setStatus] = useState(driver.accountStatus === 'pending' ? 'pending' : driver.accountStatus);
  useEffect(() => { setStatus(driver.accountStatus); }, [driver.accountStatus]);
  const canActivate = driver.qualificationStatus === 'approved' && (driver.licenseExpiresAt || 0) > Date.now() && (driver.healthExpiresAt || 0) > Date.now();
  const blocked = status === 'active' && driver.accountStatus !== 'active' && !canActivate;
  return <form onSubmit={e => { e.preventDefault(); saveAccount({ status, driverId: driver.id }); }}><input aria-label={`Mã tài xế ${driver.displayName} (tự động)`} value={driver.employeeCode || ''} placeholder="Tự cấp mã khi lưu tài khoản" readOnly /><select name="status" aria-label={`Trạng thái tài khoản ${driver.displayName}`} value={status} onChange={e => setStatus(e.target.value)}>{driver.accountStatus === 'pending' && <option value="pending" disabled>Chờ kích hoạt</option>}<option value="active">Hoạt động</option><option value="locked">Khóa</option><option value="rejected">Từ chối</option></select>{blocked && <small>Cần duyệt hồ sơ nghề nghiệp trước khi kích hoạt.</small>}<button disabled={busy || blocked || status === 'pending'}>Lưu tài khoản</button><small>Mã tài xế được cấp tự động lần đầu. Nút này lưu trạng thái tài khoản, không duyệt giấy tờ.</small></form>;
}
