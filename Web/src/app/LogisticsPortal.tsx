import { FormEvent, ReactNode, useEffect, useState } from 'react';
import { command, CompanyAccount, Alert, Driver, FleetVehicle, labels, Leave, Order, Report, Staff, Trip, watch, qualificationLabel } from '../api/dispatch';
import { AlertTriangle, Bell, Car, ChevronRight, CircleUserRound, ClipboardList, FileBarChart, LayoutDashboard, LogOut, Menu, Moon, Package, Search, ShieldCheck, Sun, Truck, UserCog, Users, X } from 'lucide-react';
import DriverManagement from './DriverManagement';
import TripAssignmentForm, { driverAssignmentIssues } from './TripAssignmentForm';
import DriverProfileReview from './DriverProfileReview';
import AccountDirectory from './AccountDirectory';
import ErrorNotice from './ErrorNotice';
import RequestNotice from './RequestNotice';
import StaffIdentity from './StaffIdentity';
import { warmBusinessServer } from '../api/businessFunctions';
import StaffAccessForm from './StaffAccessForm';
import TransportOrderForm from './TransportOrderForm';
import FleetOverview from './FleetOverview';
import './logistics.css';

const date = (v?: number) => v ? new Date(v).toLocaleString('vi-VN') : '—';
const state = (v: string) => labels[v] || v;
const idLabel = (v: string) => v.slice(0, 8);
type Tab = 'dashboard' | 'orders' | 'trips' | 'drivers' | 'vehicles' | 'leaves' | 'reports' | 'alerts' | 'staff' | 'accounts' | 'profile';
const tabs: [Tab, string][] = [['dashboard', 'Tổng quan'], ['orders', 'Đơn vận chuyển'], ['trips', 'Điều phối chuyến'], ['drivers', 'Tài xế'], ['vehicles', 'Đội xe'], ['leaves', 'Đơn nghỉ'], ['reports', 'Báo cáo quan sát'], ['alerts', 'Cảnh báo buồn ngủ'], ['staff', 'Nhân viên web'], ['accounts', 'Tài khoản đăng ký'], ['profile', 'Hồ sơ của tôi']];
const tabIcons = { dashboard: LayoutDashboard, orders: Package, trips: Truck, drivers: Users, vehicles: Car, leaves: ClipboardList, reports: FileBarChart, alerts: AlertTriangle, staff: UserCog, accounts: Users, profile: CircleUserRound };
function Field({ name, label, type = 'text', value, required = true, placeholder }: { name: string; label: string; type?: string; value?: string | number; required?: boolean; placeholder?: string }) {
  return <label>{label}<input name={name} type={type} defaultValue={value} placeholder={placeholder || ({ plate: "VD: 51C-123.45", capacityKg: "VD: 5000", name: "VD: Bình Dương → Khánh Hòa", reason: "VD: Tài xế nghỉ đột xuất, chuyển anh Nam thực hiện", employeeCode: "VD: NV003", displayName: "VD: Nguyễn Văn Nam", staffId: "UID từ danh sách tài khoản", location: "VD: Bãi xe công ty tại Nha Trang", cargoNote: "VD: Hàng nguyên kiện, còn 2 điểm giao" } as Record<string, string>)[name]} required={required} min={type === 'number' ? 0 : undefined} step={type === 'number' ? 'any' : undefined} /></label>;
}
function Dialog({ title, close, children }: { title: string; close: () => void; children: ReactNode }) {
  return <div className="log-dialog-backdrop"><section role="dialog" aria-modal="true" aria-label={title} className="log-dialog"><header><h2>{title}</h2><button type="button" onClick={close} aria-label="Đóng">×</button></header>{children}</section></div>;
}
function Status({ value }: { value: string }) { return <span className={`log-status log-${value}`}>{state(value)}</span>; }

export default function LogisticsPortal({ staff, logout }: { staff: Staff; logout: () => void }) {
  const [tab, setTab] = useState<Tab>('dashboard');
  const [orders, setOrders] = useState<Order[]>([]), [trips, setTrips] = useState<Trip[]>([]);
  const [drivers, setDrivers] = useState<Driver[]>([]), [vehicles, setVehicles] = useState<FleetVehicle[]>([]);
  const [leaves, setLeaves] = useState<Leave[]>([]), [reports, setReports] = useState<Report[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({}), [loaded, setLoaded] = useState<string[]>([]);
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [errorNotice, setErrorNotice] = useState('');
  const [requestLabel, setRequestLabel] = useState('Đang lưu thay đổi');
  useEffect(() => { warmBusinessServer(); }, []);
  useEffect(() => { if (error) setErrorNotice(error); }, [error]);
  const [dialog, setDialog] = useState<'order' | 'trip' | 'vehicle' | null>(null);
  const [selectedTrip, setSelectedTrip] = useState<Trip | null>(null);
  const [editVehicle, setEditVehicle] = useState<FleetVehicle | null>(null);
  const [search, setSearch] = useState('');
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [staffMembers, setStaffMembers] = useState<(Staff & { id: string })[]>([]);
  const [selectedDriverId, setSelectedDriverId] = useState<string | null>(null);
  const selectedDriver = drivers.find(d => d.id === selectedDriverId);
  const [staffAccount, setStaffAccount] = useState<CompanyAccount | null>(null);
  const [menu, setMenu] = useState(false);
  const [dark, setDark] = useState(() => localStorage.getItem('driverguard_theme') !== 'light');
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    localStorage.setItem('driverguard_theme', dark ? 'dark' : 'light');
  }, [dark]);
  const admin = staff.role === 'admin';
  useEffect(() => {
    const subscribe = <T,>(name: string, setter: (rows: T[]) => void) => watch<T>(name, rows => {
      setter(rows); setLoaded(v => v.includes(name) ? v : [...v, name]);
      setErrors(v => { const next = { ...v }; delete next[name]; return next; });
    }, e => setErrors(v => ({ ...v, [name]: e.message })));
    const subscriptions = [subscribe('transportOrders', setOrders), subscribe('dispatchTrips', setTrips), subscribe('drivers', setDrivers), subscribe('fleetVehicles', setVehicles), subscribe('leaveRequests', setLeaves), subscribe('trips', setReports), subscribe('alerts', setAlerts)];
    if (staff.role === 'admin') subscriptions.push(subscribe('staff', setStaffMembers));
    return () => subscriptions.forEach(u => u());
  }, [staff.role]);
  useEffect(() => { if (selectedTrip) { const fresh = trips.find(t => t.id === selectedTrip.id); if (fresh) setSelectedTrip(fresh); } }, [trips]);
  async function run(action: string, data: Record<string, unknown>, onSuccess?: () => void) {
    if (busy) return;
    setRequestLabel(action === 'reviewDriverDossier' ? (data.status === 'approved' ? 'Đang duyệt hồ sơ' : 'Đang gửi yêu cầu bổ sung hồ sơ') : 'Đang lưu thay đổi');
    setBusy(true); setError(''); setErrorNotice('');
    try { await command(action, data); onSuccess?.(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không lưu được. Kiểm tra mạng rồi thử lại.'); }
    finally { setBusy(false); }
  }
  function formValues(e: FormEvent<HTMLFormElement>) { e.preventDefault(); return Object.fromEntries(new FormData(e.currentTarget).entries()); }
  const activeTrips = trips.filter(t => !['completed', 'completed_issues', 'cancelled'].includes(t.status));
  const driverName = (id: string) => drivers.find(d => d.id === id)?.displayName || idLabel(id);
  const match = (v: unknown) => JSON.stringify(v).toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi'));
  const openDialog = (v: typeof dialog) => { setError(''); setDialog(v); };
  const unreadAlerts = alerts.filter(a => !a.status || a.status === 'NEW').length;
  function selectTab(key: Tab) { setTab(key); setSearch(''); setError(''); setMenu(false); }
  return <div className="app-shell fleet-shell" onInvalidCapture={event => {
    const field = event.target as HTMLInputElement;
    // Browsers fire invalid for every missing field; show the first one the browser focuses.
    if (field.form?.querySelector(':invalid') !== field) return;
    const label = field.labels?.[0]?.firstChild?.textContent?.trim() || 'Thông tin';
    setErrorNotice(`${label}: ${field.validity.valueMissing ? 'Vui lòng điền hoặc chọn mục này.' : 'Giá trị chưa đúng, vui lòng kiểm tra lại.'}`);
  }}>
    <ErrorNotice message={errorNotice} dismiss={() => setErrorNotice('')} />
    <RequestNotice active={busy} label={requestLabel} />
    {menu && <button className="sidebar-backdrop" onClick={() => setMenu(false)} aria-label="Đóng menu" />}
    <aside className={`sidebar ${menu ? 'sidebar-open' : ''}`}>
      <div className="sidebar-brand"><span className="brand-mark"><ShieldCheck /></span><div><strong>DrowsyGuard</strong><small>{admin ? 'Admin Portal' : 'Điều phối vận tải'}</small></div><button className="mobile-close" onClick={() => setMenu(false)} aria-label="Đóng menu"><X /></button></div>
      <nav>{tabs.filter(([key]) => !['staff', 'accounts'].includes(key) || admin).map(([key, label]) => { const Icon = tabIcons[key]; return <button key={key} className={tab === key ? 'active' : ''} onClick={() => selectTab(key)}><Icon />{label}{key === 'alerts' && unreadAlerts > 0 && <em>{unreadAlerts}</em>}</button>; })}</nav>
      <div className="fleet-sidebar-user"><strong>{staff.displayName || staff.employeeCode}</strong><small>{admin ? 'Quản trị / quản lý' : 'Điều phối viên'}</small></div>
      <button className="logout-button" onClick={logout}><LogOut />Đăng xuất</button>
    </aside>
    <div className="main-column">
      <header className="topbar">
        <button aria-label="Mở menu" className="menu-button" onClick={() => setMenu(true)}><Menu /></button>
        <div className="topbar-breadcrumb"><span>DriverGuard</span><ChevronRight /><strong>{tabs.find(([key]) => key === tab)?.[1]}</strong></div>
        {tab !== 'dashboard' && <label className="topbar-search"><Search /><input aria-label="Tìm trong trang" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm mã, tên, địa chỉ…" /></label>}
        <div className="topbar-actions"><button className="theme-toggle" aria-label={dark ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"} title={dark ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"} aria-pressed={dark} onClick={() => setDark(v => !v)}>{dark ? <Moon /> : <Sun />}<span>{dark ? "Tối" : "Sáng"}</span></button><button aria-label="Xem cảnh báo" onClick={() => selectTab('alerts')}><Bell />{unreadAlerts > 0 && <span className="notification-dot" />}</button><button className="admin-avatar" title={staff.displayName} aria-label="Mở hồ sơ của tôi" onClick={() => selectTab("profile")}><CircleUserRound /></button></div>
      </header>
      <main className="content"><div className="page-stack log-workspace">
        <div className="page-heading"><div><p>TRUNG TÂM ĐIỀU HÀNH</p><h1>{tabs.find(([key]) => key === tab)?.[1]}</h1><span>{tab === 'dashboard' ? 'Tổng quan vận tải và cảnh báo tài xế từ DriverGuard.' : 'Quản lý chuyến hàng và theo dõi kết quả thực hiện.'}</span></div><span className="badge badge-blue">{import.meta.env.VITE_FIREBASE_EMULATORS === 'true' ? 'Môi trường local' : 'Firebase thật'}</span></div>
        {tab === 'dashboard' && <FleetOverview orders={orders} trips={trips} leaves={leaves} alerts={alerts} driverName={driverName} openAlerts={() => selectTab('alerts')} openTrips={() => selectTab('trips')} />}
      {loaded.length < 7 && !Object.keys(errors).length && <p role="status">Đang tải dữ liệu vận tải…</p>}
      {Object.entries(errors).map(([name, message]) => <div role="alert" className="log-error" key={name}>Không đọc được {name}: {message}</div>)}
      {tab !== 'dashboard' && <div className="log-toolbar"><input aria-label="Tìm kiếm" placeholder="Tìm mã, tên, địa chỉ…" value={search} onChange={e => setSearch(e.target.value)} />{tab === 'orders' && <button onClick={() => { openDialog('order'); }}>+ Tạo đơn</button>}{tab === 'trips' && <button onClick={() => { openDialog('trip'); }}>+ Lập và giao chuyến</button>}{tab === 'vehicles' && admin && <button onClick={() => { setEditVehicle(null); openDialog('vehicle'); }}>+ Thêm xe</button>}</div>}

      {tab === 'accounts' && admin && <AccountDirectory search={search} openDriver={uid => { setError(''); setSelectedDriverId(uid); }} grantStaff={account => { setStaffAccount(account); selectTab('staff'); }} />}
      {tab === 'profile' && <article className="panel staff-own-profile"><h2>Hồ sơ nhân viên</h2><p>{staff.email} · {staff.employeeCode} · {admin ? 'Quản trị' : 'Điều phối viên'}</p><form onSubmit={e => { const f = formValues(e); void run('updateMyStaffProfile', f); }}><Field name="displayName" label="Họ và tên" value={staff.displayName} placeholder="VD: Nguyễn Văn Nam" /><Field name="phone" label="Điện thoại liên hệ" value={staff.phone} type="tel" placeholder="VD: 0901234567" /><button disabled={busy}>Lưu hồ sơ của tôi</button></form><small>Email, mã nhân viên và quyền truy cập do quản trị quản lý.</small></article>}
      {tab === 'staff' && admin && <><StaffAccessForm account={staffAccount} employee={staffMembers.find(s => s.id === staffAccount?.uid)} busy={busy} chooseAccount={() => selectTab('accounts')} save={data => void run('saveStaff', data)} /><div className="log-table"><table><thead><tr><th>Nhân viên</th><th>UID</th><th>Vai trò / liên hệ</th><th>Quyền</th></tr></thead><tbody>{staffMembers.map(s => <tr key={s.id}><td>{s.displayName}<small>{s.employeeCode}</small></td><td>{s.id}</td><td>{s.role}<small>{s.phone || "Chưa có SĐT"}</small><small>{s.email}</small></td><td>{s.active ? 'Hoạt động' : 'Đã thu hồi'}</td></tr>)}</tbody></table></div></>}
      {tab === 'orders' && <div className="log-table"><table><thead><tr><th>Đơn / khách hàng</th><th>Nhận → giao</th><th>Hàng hóa</th><th>Thời gian</th><th>Trạng thái</th></tr></thead><tbody>{orders.filter(match).map(o => <tr key={o.id}><td><strong>{idLabel(o.id)} · {o.customer}</strong><small>{o.customerPhone}</small><small>Người tạo: <StaffIdentity uid={o.createdBy} name={o.createdByName} email={o.createdByEmail} /></small></td><td><strong>{o.sender} · {o.senderPhone}</strong><small>{o.pickupAddress}</small><strong>→ {o.receiver} · {o.receiverPhone}</strong><small>{o.deliveryAddress}</small></td><td>{o.items.map((i, k) => <small key={k}>{i.name}: {i.quantity} {i.unit}</small>)}<strong>{o.weightKg} kg</strong><small>{o.note}</small></td><td>{date(o.start)}<small>Hạn giao: {date(o.end)}</small></td><td><Status value={o.status} /><small>{o.tripId && `Chuyến ${idLabel(o.tripId)}`}</small></td></tr>)}</tbody></table>{!orders.length && <p className="log-empty">Chưa có đơn vận chuyển. Tạo đơn trước khi lập chuyến.</p>}</div>}
      {tab === 'trips' && <div className="log-cards">{trips.filter(match).sort((a, b) => b.updatedAt - a.updatedAt).map(t => <article key={t.id}><div className="log-between"><h3>{t.name}</h3><Status value={t.status} /></div><p>{idLabel(t.id)} · {t.vehiclePlate} · {t.driverName}</p><p>{date(t.start)} → {date(t.end)}</p><p>{t.orderIds.length} đơn · {t.stops.filter(s => s.status !== 'pending').length}/{t.stops.length} điểm đã xử lý</p><small>Điều phối: <StaffIdentity uid={t.dispatcherId} name={t.dispatcherName} email={t.dispatcherEmail} /> · phân công v{t.assignmentVersion}</small><small>Cập nhật nghiệp vụ cuối: {date(t.updatedAt)} (không phải vị trí GPS trực tiếp)</small>{t.declineReason && <p>Lý do từ chối: {t.declineReason}</p>}<button onClick={() => { setError(''); setSelectedTrip(t); }}>Xem chuyến / xử lý</button></article>)}{!trips.length && <p className="log-empty">Chưa có chuyến. Chọn đơn, tài xế và xe để phân công.</p>}</div>}
      {tab === 'drivers' && <DriverManagement drivers={drivers} trips={trips} admin={admin} busy={busy} search={search} openProfile={uid => { setError(''); setSelectedDriverId(uid); }} saveAccount={data => void run('driverStatus', data)} />}
      {tab === 'vehicles' && <div className="log-cards">{vehicles.filter(match).map(v => <article key={v.id}><h3>{v.plate}</h3><p>Tải trọng: {v.capacityKg} kg</p><Status value={v.status} /><p>{activeTrips.filter(t => t.vehicleId === v.id).length} chuyến chưa kết thúc</p>{admin && <button onClick={() => { setEditVehicle(v); openDialog('vehicle'); }}>Sửa xe</button>}</article>)}</div>}
      {tab === 'leaves' && <div className="log-cards">{leaves.filter(match).sort((a, b) => b.start - a.start).map(l => <article key={l.id}><div className="log-between"><h3>{driverName(l.driverId)}</h3><Status value={l.status} /></div><p>{date(l.start)} → {date(l.end)}</p><p>{l.reason}</p>{l.status === 'pending' && <p>Chuyến có thể ảnh hưởng: {activeTrips.filter(t => t.driverId === l.driverId && t.start < l.end && l.start < t.end).map(t => t.name).join(', ') || 'Không có theo lịch hiện tại'}</p>}{l.affectedTripIds?.length ? <p>{l.affectedTripIds.length} chuyến cần phân công lại.</p> : null}{admin && l.status === 'pending' && <div className="log-actions"><button disabled={busy} onClick={() => void run('reviewLeave', { leaveId: l.id, status: 'approved' })}>Duyệt nghỉ</button><button disabled={busy} onClick={() => void run('reviewLeave', { leaveId: l.id, status: 'rejected' })}>Từ chối</button></div>}</article>)}</div>}
      {tab === 'alerts' && <div className="log-cards">{alerts.filter(match).sort((a, b) => b.timestamp - a.timestamp).map(a => <article key={a.id}><h3>{driverName(a.userId)} · {date(a.timestamp)}</h3><p>Chuyến: {a.dispatchTripId ? trips.find(t => t.id === a.dispatchTripId)?.name || idLabel(a.dispatchTripId) : 'Phiên độc lập / dữ liệu cũ'}</p><p>EAR: {a.ear?.toFixed(3) ?? '—'} · mắt nhắm {a.closedDurationSec?.toFixed(1) ?? '—'} giây</p>{a.latitude != null && a.longitude != null && <a href={`https://www.google.com/maps/search/?api=1&query=${a.latitude},${a.longitude}`} target="_blank" rel="noreferrer">Vị trí ghi nhận cảnh báo</a>}<form onSubmit={e => { const f = formValues(e); void run('reviewAlert', { eventId: a.id, ...f }); }}><label>Trạng thái xử lý<select name="status" defaultValue={a.status || 'NEW'}><option value="NEW">Mới</option><option value="ACKNOWLEDGED">Đã xác nhận</option><option value="RESOLVED">Đã xử lý</option></select></label><Field name="reason" label="Ghi chú điều phối" value={a.note} required={false} /><button disabled={busy}>Lưu xử lý</button></form></article>)}</div>}
      {tab === 'reports' && <><p>Điểm theo quy tắc thử nghiệm trên dữ liệu mắt quan sát được. Mỗi phiên giữ đúng tài xế và phiên bản phân công.</p><div className="log-table"><table><thead><tr><th>Chuyến / phiên</th><th>Tài xế</th><th>Thời điểm</th><th>Điểm / độ bao phủ</th><th>Nhận xét</th></tr></thead><tbody>{reports.filter(match).sort((a, b) => b.startTime - a.startTime).map(r => <tr key={r.id}><td>{r.dispatchTripId ? trips.find(t => t.id === r.dispatchTripId)?.name || idLabel(r.dispatchTripId) : 'Phiên giám sát độc lập'}<small>Phiên {idLabel(r.id)} · v{r.assignmentVersion ?? '—'}</small></td><td>{driverName(r.userId)}</td><td>{date(r.startTime)}</td><td>{r.hasSufficientData && r.safetyScore != null ? `${r.safetyScore}/100` : 'Chưa đủ dữ liệu'}<small>Quan sát {((r.measurementCoverage || 0) * 100).toFixed(0)}% · {r.totalAlerts} cảnh báo</small></td><td>{r.aiDiagnosis}</td></tr>)}</tbody></table></div></>}
    </div></main>
    </div>


    {dialog === 'order' && <Dialog title="Tạo đơn vận chuyển" close={() => !busy && setDialog(null)}><TransportOrderForm busy={busy} error={error} save={data => void run('createOrder', data, () => setDialog(null))} /></Dialog>}
    {dialog === 'vehicle' && <Dialog title={editVehicle ? 'Cập nhật xe' : 'Thêm xe công ty'} close={() => !busy && setDialog(null)}><form onSubmit={e => { const f = formValues(e); void run('saveVehicle', { ...f, capacityKg: Number(f.capacityKg), ...(editVehicle ? { vehicleId: editVehicle.id } : {}) }, () => setDialog(null)); }}><Field name="plate" label="Biển số" value={editVehicle?.plate} /><Field name="capacityKg" label="Tải trọng (kg)" type="number" value={editVehicle?.capacityKg} /><label>Trạng thái<select name="status" defaultValue={editVehicle?.status || 'available'}><option value="available">Sẵn sàng</option><option value="maintenance">Bảo trì</option><option value="inactive">Ngừng sử dụng</option></select></label>{error && <p className="log-error">{error}</p>}<button disabled={busy}>Lưu xe</button></form></Dialog>}
    {dialog === 'trip' && <Dialog title="Lập và giao chuyến" close={() => !busy && setDialog(null)}><TripAssignmentForm orders={orders} drivers={drivers} vehicles={vehicles} admin={admin} busy={busy} error={error} save={data => void run('assignTrip', data, () => setDialog(null))} openProfile={uid => { setError(''); setSelectedDriverId(uid); }} /></Dialog>}

    {selectedTrip && <Dialog title={selectedTrip.name} close={() => !busy && setSelectedTrip(null)}><Status value={selectedTrip.status} /><p>{selectedTrip.driverName} · {selectedTrip.vehiclePlate} · v{selectedTrip.assignmentVersion}</p><p>Kế hoạch: {date(selectedTrip.start)} → {date(selectedTrip.end)}</p><p>Thực tế: {date(selectedTrip.actualStart)} → {date(selectedTrip.actualEnd)}</p><ol>{selectedTrip.stops.map(s => <li key={s.id}><strong>{s.kind === 'pickup' ? 'Nhận' : 'Giao'}: {s.address}</strong><p>{s.contact} · {s.phone} · <Status value={s.status} /></p>{s.items.map((item, i) => <small key={i}>{item.name}: {s.quantities?.[i] ?? '—'}/{item.quantity} {item.unit}</small>)}{s.actualContact && <p>Thực tế: {s.actualContact} · {date(s.completedAt)} · tài xế {driverName(s.driverId || '')}</p>}{s.note && <p>{s.note}</p>}</li>)}</ol><h3>Lịch sử phân công</h3>{selectedTrip.history.map((h, i) => <p key={i}>{date(h.at)} · {h.from ? driverName(h.from) : 'Ban đầu'} → {driverName(h.to)} · người giao <StaffIdentity uid={h.by} name={h.byName} email={h.byEmail} /><small>{h.reason} {h.location} {h.cargoNote}</small></p>)}
      {['assigned', 'accepted', 'needs_reassignment', 'declined', 'in_progress', 'handover_pending'].includes(selectedTrip.status) && <form onSubmit={e => { const f = formValues(e); void run(['in_progress', 'handover_pending'].includes(selectedTrip.status) ? 'handover' : 'reassignTrip', { ...f, tripId: selectedTrip.id }); }}><h3>{['in_progress', 'handover_pending'].includes(selectedTrip.status) ? 'Xác nhận bàn giao giữa chuyến' : 'Chọn tài xế thay thế'}</h3><label>Tài xế thay<select name="driverId" required><option value="">Chọn người thay</option>{drivers.filter(d => d.id !== selectedTrip.driverId).map(d => { const reasons = driverAssignmentIssues(d, Math.max(selectedTrip.end, Date.now()), selectedTrip.vehicleId); return <option key={d.id} value={d.id} disabled={!!reasons.length}>{d.employeeCode || idLabel(d.id)} · {d.displayName}{reasons.length ? ` — ${reasons[0]}` : ''}</option>; })}</select></label><Field name="reason" label="Lý do" />{['in_progress', 'handover_pending'].includes(selectedTrip.status) && <><Field name="location" label="Vị trí bàn giao" /><Field name="cargoNote" label="Tình trạng hàng và điểm còn lại" /><p>Người nhận phải xác nhận trên app. Các điểm và báo cáo trước đó vẫn giữ theo người cũ.</p></>}<button disabled={busy}>Kiểm tra lịch và giao lại</button></form>}
      {['assigned', 'accepted', 'needs_reassignment', 'declined'].includes(selectedTrip.status) && <form onSubmit={e => { const f = formValues(e); void run('cancelTrip', { tripId: selectedTrip.id, ...f }); }}><Field name="reason" label="Lý do hủy chuyến" /><button disabled={busy}>Hủy chuyến và trả đơn về chờ</button></form>}
      {selectedTrip.status === 'completed_issues' && <form onSubmit={e => { const f = formValues(e); void run('resolveIssue', { tripId: selectedTrip.id, ...f }); }}><p>Vấn đề: {selectedTrip.issueReason}</p>{selectedTrip.resolution && <p>Đã xử lý: {selectedTrip.resolution.note}</p>}<Field name="reason" label="Kết quả xử lý / phương án tiếp theo" /><button disabled={busy}>Ghi nhận xử lý</button></form>}
      {error && <p className="log-error" role="alert">{error}</p>}
    </Dialog>}
    {selectedDriver && <Dialog title="Hồ sơ tài xế" close={() => !busy && setSelectedDriverId(null)}><DriverProfileReview key={selectedDriver.id} vehicles={vehicles} driver={selectedDriver} admin={admin} busy={busy} error={error} review={data => void run('reviewDriverDossier', data)} recordPhysical={data => void run('recordPhysicalDossier', data)} /></Dialog>}
  </div>;
}
