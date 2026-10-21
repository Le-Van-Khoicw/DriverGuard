import { useEffect, useState } from 'react';
import { CompanyAccount, labels, listCompanyAccounts } from '../api/dispatch';

export default function AccountDirectory({ openDriver, grantStaff, search }: { search: string; openDriver: (uid: string) => void; grantStaff: (account: CompanyAccount) => void }) {
  const [accounts, setAccounts] = useState<CompanyAccount[]>([]), [next, setNext] = useState<string | null>(null);
  const [group, setGroup] = useState('all');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function load(append = false) {
    setBusy(true); setError('');
    try { const page = await listCompanyAccounts(append ? next || undefined : undefined); setAccounts(v => append ? [...v, ...page.accounts].filter((x, i, all) => all.findIndex(a => a.uid === x.uid) === i) : page.accounts); setNext(page.nextPageToken); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không tải được tài khoản'); }
    finally { setBusy(false); }
  }
  useEffect(() => { void load(); }, []);
  return <>
    <p className="account-help">Email là thông tin đăng nhập. Quyền được cấp theo UID và vai trò, không theo @gmail.com hay email công ty. Một tài khoản có thể có hồ sơ tài xế và quyền nhân viên nếu quản lý cấp cả hai.</p>
    <div className="log-actions"><select aria-label="Lọc loại tài khoản" value={group} onChange={e => setGroup(e.target.value)}><option value="all">Tất cả tài khoản đã tải</option><option value="drivers">Có hồ sơ tài xế</option><option value="staff">Có quyền nhân viên</option><option value="unassigned">Chưa có vai trò công ty</option></select><button onClick={() => void load()} disabled={busy}>Tải lại tài khoản</button></div>
    {import.meta.env.VITE_FIREBASE_EMULATORS === 'true' && <p className="account-help">Đang xem Auth local của demo-driverguard. Các tài khoản trên Firebase thật không tự xuất hiện trong môi trường này.</p>}
    {error && <p className="log-error" role="alert">{error}</p>}
    <div className="log-table"><table><thead><tr><th>Hồ sơ / email</th><th>Cách đăng nhập</th><th>Vai trò / tình trạng</th><th>Thời điểm</th><th>Thao tác</th></tr></thead><tbody>{accounts.filter(a => JSON.stringify(a).toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi'))).filter(a => group === 'all' || (group === 'drivers' && a.driverStatus) || (group === 'staff' && a.staffRole) || (group === 'unassigned' && !a.driverStatus && !a.staffRole)).map(a => <tr key={a.uid}><td><strong>{a.displayName || 'Chưa đặt tên'}</strong><small>{a.email || 'Không có email'}</small><small>UID: {a.uid}</small></td><td>{a.providers.map(p => p === 'password' ? 'Email / mật khẩu' : p === 'google.com' ? 'Google' : p).join(', ') || 'Chưa có provider'}<small>{a.emailVerified ? 'Email đã xác minh' : 'Email chưa xác minh'}</small></td><td>{a.staffRole ? `${a.staffRole === 'admin' ? 'Quản trị' : 'Điều phối'} · ${a.staffActive ? 'đang cấp quyền' : 'đã thu hồi'}` : ''}{a.driverStatus && <small>Tài xế · {labels[a.driverStatus] || a.driverStatus}</small>}{!a.staffRole && !a.driverStatus && 'Chưa có vai trò công ty'}{a.disabled && <small>Tài khoản Auth đã bị vô hiệu hóa</small>}</td><td><small>Tạo: {new Date(a.createdAt).toLocaleString('vi-VN')}</small><small>Đăng nhập: {a.lastSignInAt ? new Date(a.lastSignInAt).toLocaleString('vi-VN') : 'Chưa có'}</small></td><td><div className="account-actions">{a.driverStatus && <button onClick={() => openDriver(a.uid)}>Hồ sơ tài xế</button>}<button onClick={() => grantStaff(a)}>Cấp / sửa quyền web</button></div></td></tr>)}</tbody></table></div>
    {next && <button disabled={busy} onClick={() => void load(true)}>{busy ? 'Đang tải…' : 'Xem thêm tài khoản'}</button>}
  </>;
}
