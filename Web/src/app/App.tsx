import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity, AlertTriangle, BarChart3, Bell, Camera, Car, ChevronRight,
  CircleUserRound, Download, Eye, EyeOff, Gauge, LayoutDashboard, LoaderCircle,
  HeartPulse, History, Link2, LockKeyhole, LogOut, Menu, Moon, Plus, RefreshCw, Search, Settings2, ShieldCheck,
  Sun, Users, Video, Wifi, WifiOff, X,
} from "lucide-react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import {
  api, AlertTrendPoint, DashboardSummary, Device, DrowsinessEvent, RecentAlert,
  MonitoringSession, User, Vehicle,
} from "../api/client";
import { AuditPage, BindingsPage, GlobalSearchPage, HealthPage, SettingsPage } from "./ManagementPages";

type View = "dashboard" | "devices" | "bindings" | "users" | "vehicles" | "sessions" | "alerts" | "settings" | "health" | "audit" | "search";
type DataState = {
  summary: DashboardSummary | null;
  trend: AlertTrendPoint[];
  users: User[];
  devices: Device[];
  vehicles: Vehicle[];
  sessions: MonitoringSession[];
  events: DrowsinessEvent[];
  recentAlerts: RecentAlert[];
};

const EMPTY_DATA: DataState = {
  summary: null, trend: [], users: [], devices: [], vehicles: [], sessions: [], events: [], recentAlerts: [],
};

const NAV = [
  { id: "dashboard" as const, label: "Tổng quan", icon: LayoutDashboard },
  { id: "devices" as const, label: "Thiết bị", icon: Camera },
  { id: "bindings" as const, label: "Gán thiết bị", icon: Link2 },
  { id: "users" as const, label: "Tài xế", icon: Users },
  { id: "vehicles" as const, label: "Phương tiện", icon: Car },
  { id: "sessions" as const, label: "Phiên giám sát", icon: Video },
  { id: "alerts" as const, label: "Cảnh báo", icon: AlertTriangle },
  { id: "settings" as const, label: "Cấu hình AI", icon: Settings2 },
  { id: "health" as const, label: "Sức khỏe thiết bị", icon: HeartPulse },
  { id: "audit" as const, label: "Nhật ký", icon: History },
  { id: "search" as const, label: "Tìm kiếm", icon: Search },
];

function formatDate(value?: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function Badge({ children, tone = "gray" }: { children: React.ReactNode; tone?: "green" | "red" | "amber" | "blue" | "gray" }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

function Login({ onSuccess }: { onSuccess: () => void }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      await api.login(username.trim(), password);
      onSuccess();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Đăng nhập thất bại");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-shell">
        <div className="login-brand">
          <div className="brand-lockup"><span className="brand-mark"><ShieldCheck /></span><div><strong>DrowsyGuard</strong><small>ADMIN CONTROL CENTER</small></div></div>
          <div className="brand-copy">
            <Badge tone="green"><span className="live-dot" /> Hệ thống giám sát an toàn</Badge>
            <h1>Quản lý cảnh báo buồn ngủ từ một trung tâm duy nhất.</h1>
            <p>Theo dõi thiết bị, tài xế, phương tiện và sự kiện cảnh báo được đồng bộ từ ứng dụng DriverGuard.</p>
          </div>
          <div className="brand-features">
            <span><Gauge /> Theo dõi trạng thái thiết bị</span>
            <span><Activity /> Thống kê cảnh báo theo thời gian</span>
            <span><ShieldCheck /> Dữ liệu quản trị được bảo vệ bằng JWT</span>
          </div>
        </div>
        <div className="login-form-panel">
          <div className="mobile-brand"><ShieldCheck /> DrowsyGuard</div>
          <div className="login-heading"><p>CỔNG QUẢN TRỊ</p><h2>Đăng nhập hệ thống</h2><span>Sử dụng tài khoản quản trị được cấp bởi hệ thống.</span></div>
          <form onSubmit={submit} className="login-form">
            <label>Tên đăng nhập<input autoFocus value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Nhập username" required /></label>
            <label>Mật khẩu<div className="password-field"><input value={password} onChange={(e) => setPassword(e.target.value)} type={showPassword ? "text" : "password"} placeholder="Nhập mật khẩu" required /><button type="button" aria-label="Hiện hoặc ẩn mật khẩu" onClick={() => setShowPassword((v) => !v)}>{showPassword ? <EyeOff /> : <Eye />}</button></div></label>
            {error && <div className="form-error"><AlertTriangle />{error}</div>}
            <button className="primary-button login-button" disabled={loading}>{loading ? <><LoaderCircle className="spin" />Đang xác thực</> : <>Đăng nhập<ChevronRight /></>}</button>
          </form>
          <div className="login-note"><LockKeyhole /> Chỉ tài khoản Admin mới có quyền truy cập Web Dashboard.</div>
        </div>
      </section>
    </main>
  );
}

function Sidebar({ view, setView, open, close, logout, alerts }: { view: View; setView: (v: View) => void; open: boolean; close: () => void; logout: () => void; alerts: number }) {
  return <>
    {open && <button className="sidebar-backdrop" onClick={close} aria-label="Đóng menu" />}
    <aside className={`sidebar ${open ? "sidebar-open" : ""}`}>
      <div className="sidebar-brand"><span className="brand-mark"><ShieldCheck /></span><div><strong>DrowsyGuard</strong><small>Admin Portal</small></div><button className="mobile-close" onClick={close}><X /></button></div>
      <nav>{NAV.filter(item => item.id !== "search").map(({ id, label, icon: Icon }) => <button key={id} className={view === id ? "active" : ""} onClick={() => { setView(id); close(); }}><Icon />{label}{id === "alerts" && alerts > 0 && <em>{alerts}</em>}</button>)}</nav>
      <button className="logout-button" onClick={logout}><LogOut />Đăng xuất</button>
    </aside>
  </>;
}

function Stat({ label, value, note, icon: Icon, tone }: { label: string; value: string | number; note: string; icon: React.ElementType; tone: string }) {
  return <article className="stat-card"><span className={`stat-icon ${tone}`}><Icon /></span><div><small>{label}</small><strong>{value}</strong><p>{note}</p></div></article>;
}

function Dashboard({ data, openAlert }: { data: DataState; openAlert: (event: RecentAlert) => void }) {
  const summary = data.summary;
  const recent = data.recentAlerts;
  return <div className="page-stack">
    <div className="page-heading"><div><p>TRUNG TÂM ĐIỀU HÀNH</p><h1>Tổng quan hệ thống</h1><span>Dữ liệu đồng bộ trực tiếp từ DriverGuard Backend.</span></div><Badge tone="green"><span className="live-dot" /> Đang kết nối</Badge></div>
    <section className="stats-grid">
      <Stat label="Tổng thiết bị" value={summary?.totalDevices ?? 0} note={`${summary?.offlineDevices ?? 0} thiết bị offline`} icon={Camera} tone="blue" />
      <Stat label="Đang trực tuyến" value={summary?.onlineDevices ?? 0} note="Thiết bị gửi dữ liệu" icon={Wifi} tone="green" />
      <Stat label="Phiên hôm nay" value={summary?.sessionsToday ?? 0} note="Phiên giám sát" icon={Activity} tone="purple" />
      <Stat label="Cảnh báo hôm nay" value={summary?.alertsToday ?? 0} note={`${summary?.unhandledAlerts ?? 0} chưa xử lý`} icon={AlertTriangle} tone="red" />
    </section>
    <section className="dashboard-grid">
      <article className="panel chart-panel"><div className="panel-heading"><div><h2>Xu hướng cảnh báo</h2><p>7 ngày gần nhất</p></div><BarChart3 /></div><div className="chart-wrap"><ResponsiveContainer width="100%" height="100%"><AreaChart data={data.trend}><defs><linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#38bdf8" stopOpacity={0.4}/><stop offset="100%" stopColor="#38bdf8" stopOpacity={0}/></linearGradient></defs><CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,.12)"/><XAxis dataKey="date" tick={{ fill: "#8290a6", fontSize: 11 }} axisLine={false}/><YAxis allowDecimals={false} tick={{ fill: "#8290a6", fontSize: 11 }} axisLine={false}/><Tooltip contentStyle={{ background: "#111a28", border: "1px solid #263449", borderRadius: 10 }}/><Area type="monotone" dataKey="count" stroke="#38bdf8" fill="url(#trendFill)" strokeWidth={2}/></AreaChart></ResponsiveContainer></div></article>
      <article className="panel"><div className="panel-heading"><div><h2>Cảnh báo gần nhất</h2><p>Sự kiện mới ghi nhận</p></div><AlertTriangle /></div><div className="activity-list">{recent.length ? recent.map((event) => <button key={event.id} onClick={() => openAlert(event)}><span className={`severity ${event.eventType.toLowerCase().includes("micro") ? "critical" : "warning"}`}><AlertTriangle /></span><div><strong>{event.eventType}</strong><small>{formatDate(event.occurredAt)}</small></div><Badge tone={event.status === "NEW" ? "red" : "green"}>{event.status}</Badge></button>) : <Empty text="Chưa có cảnh báo" />}</div></article>
    </section>
  </div>;
}

function Empty({ text }: { text: string }) { return <div className="empty-state"><Search /><span>{text}</span></div>; }

function Toolbar({ title, description, search, setSearch, action }: { title: string; description: string; search: string; setSearch: (v: string) => void; action?: React.ReactNode }) {
  return <><div className="page-heading"><div><p>QUẢN TRỊ</p><h1>{title}</h1><span>{description}</span></div>{action}</div><div className="toolbar"><label className="search-box"><Search /><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm kiếm..." /></label></div></>;
}

function DevicesPage({ items, reload }: { items: Device[]; reload: () => Promise<void> }) {
  const [search, setSearch] = useState(""); const [adding, setAdding] = useState(false); const [busy, setBusy] = useState(""); const [error, setError] = useState("");
  const filtered = items.filter((d) => `${d.deviceCode} ${d.deviceName}`.toLowerCase().includes(search.toLowerCase()));
  async function toggle(device: Device) { setBusy(device.id); setError(""); try { await api.updateDevice(device.id, { status: device.status === "locked" ? "offline" : "locked" }); await reload(); } catch (e) { setError(e instanceof Error ? e.message : "Không thể cập nhật thiết bị"); } finally { setBusy(""); } }
  return <div className="page-stack"><Toolbar title="Thiết bị" description="Theo dõi camera và thiết bị biên đã đăng ký." search={search} setSearch={setSearch} action={<button className="primary-button" onClick={() => setAdding(true)}><Plus />Thêm thiết bị</button>} />{error && <div className="inline-error">{error}</div>}<div className="panel table-panel"><table><thead><tr><th>Thiết bị</th><th>Loại</th><th>Firmware / AI</th><th>Lần cuối hoạt động</th><th>Trạng thái</th><th /></tr></thead><tbody>{filtered.map((d) => <tr key={d.id}><td><strong>{d.deviceName}</strong><small>{d.deviceCode}</small></td><td>{d.deviceType}</td><td><span>{d.firmwareVersion || "—"}</span><small>{d.aiModelVersion || "Chưa cập nhật AI"}</small></td><td>{formatDate(d.lastSeenAt)}</td><td><Badge tone={d.status === "online" ? "green" : d.status === "locked" ? "amber" : "gray"}>{d.status}</Badge></td><td><button className="text-button" disabled={busy === d.id} onClick={() => toggle(d)}>{d.status === "locked" ? "Mở khóa" : "Khóa"}</button></td></tr>)}</tbody></table>{!filtered.length && <Empty text="Không tìm thấy thiết bị" />}</div>{adding && <DeviceDialog close={() => setAdding(false)} saved={async () => { setAdding(false); await reload(); }} />}</div>;
}

function DeviceDialog({ close, saved }: { close: () => void; saved: () => Promise<void> }) {
  const [form, setForm] = useState({ deviceCode: "", deviceName: "", deviceType: "edge-camera" }); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) { e.preventDefault(); setBusy(true); setError(""); try { await api.createDevice(form); await saved(); } catch (reason) { setError(reason instanceof Error ? reason.message : "Không thể thêm thiết bị"); } finally { setBusy(false); } }
  return <Modal title="Thêm thiết bị" close={close}><form className="dialog-form" onSubmit={submit}><label>Mã thiết bị<input value={form.deviceCode} onChange={(e) => setForm({ ...form, deviceCode: e.target.value })} required /></label><label>Tên thiết bị<input value={form.deviceName} onChange={(e) => setForm({ ...form, deviceName: e.target.value })} required /></label><label>Loại thiết bị<input value={form.deviceType} onChange={(e) => setForm({ ...form, deviceType: e.target.value })} required /></label>{error && <div className="form-error">{error}</div>}<div className="dialog-actions"><button type="button" className="secondary-button" onClick={close}>Hủy</button><button className="primary-button" disabled={busy}>{busy ? "Đang lưu..." : "Thêm mới"}</button></div></form></Modal>;
}

function UsersPage({ items, reload }: { items: User[]; reload: () => Promise<void> }) {
  const [search, setSearch] = useState(""); const [adding, setAdding] = useState(false); const [error, setError] = useState("");
  const filtered = items.filter((u) => `${u.fullName} ${u.phone || ""}`.toLowerCase().includes(search.toLowerCase()));
  async function toggle(user: User) { try { await api.updateUser(user.id, { is_active: !user.isActive }); await reload(); } catch (e) { setError(e instanceof Error ? e.message : "Không thể cập nhật tài xế"); } }
  return <div className="page-stack"><Toolbar title="Tài xế" description="Quản lý người dùng được đồng bộ với ứng dụng Android." search={search} setSearch={setSearch} action={<button className="primary-button" onClick={() => setAdding(true)}><Plus />Thêm tài xế</button>} />{error && <div className="inline-error">{error}</div>}<div className="panel table-panel"><table><thead><tr><th>Tài xế</th><th>Điện thoại</th><th>Ngày tạo</th><th>Trạng thái</th><th /></tr></thead><tbody>{filtered.map((u) => <tr key={u.id}><td><strong>{u.fullName}</strong><small>{u.id.slice(0, 8)}</small></td><td>{u.phone || "—"}</td><td>{formatDate(u.createdAt)}</td><td><Badge tone={u.isActive ? "green" : "red"}>{u.isActive ? "Hoạt động" : "Tạm khóa"}</Badge></td><td><button className="text-button" onClick={() => toggle(u)}>{u.isActive ? "Khóa" : "Mở khóa"}</button></td></tr>)}</tbody></table>{!filtered.length && <Empty text="Không tìm thấy tài xế" />}</div>{adding && <UserDialog close={() => setAdding(false)} saved={async () => { setAdding(false); await reload(); }} />}</div>;
}

function UserDialog({ close, saved }: { close: () => void; saved: () => Promise<void> }) {
  const [name, setName] = useState(""); const [phone, setPhone] = useState(""); const [error, setError] = useState("");
  async function submit(e: FormEvent) { e.preventDefault(); try { await api.createUser({ full_name: name, phone: phone || undefined }); await saved(); } catch (reason) { setError(reason instanceof Error ? reason.message : "Không thể thêm tài xế"); } }
  return <Modal title="Thêm tài xế" close={close}><form className="dialog-form" onSubmit={submit}><label>Họ và tên<input value={name} onChange={(e) => setName(e.target.value)} required /></label><label>Số điện thoại<input value={phone} onChange={(e) => setPhone(e.target.value)} /></label>{error && <div className="form-error">{error}</div>}<div className="dialog-actions"><button type="button" className="secondary-button" onClick={close}>Hủy</button><button className="primary-button">Thêm mới</button></div></form></Modal>;
}

function VehiclesPage({ items, users, reload }: { items: Vehicle[]; users: User[]; reload: () => Promise<void> }) {
  const [search, setSearch] = useState(""); const [adding, setAdding] = useState(false); const [error, setError] = useState("");
  const filtered = items.filter((v) => `${v.displayName} ${v.licensePlate || ""}`.toLowerCase().includes(search.toLowerCase())); const userName = (id: string) => users.find((u) => u.id === id)?.fullName || id.slice(0, 8);
  async function remove(id: string) { if (!confirm("Xóa phương tiện này?")) return; try { await api.deleteVehicle(id); await reload(); } catch (e) { setError(e instanceof Error ? e.message : "Không thể xóa phương tiện"); } }
  return <div className="page-stack"><Toolbar title="Phương tiện" description="Quản lý phương tiện thuộc từng tài xế." search={search} setSearch={setSearch} action={<button className="primary-button" onClick={() => setAdding(true)} disabled={!users.length}><Plus />Thêm phương tiện</button>} />{error && <div className="inline-error">{error}</div>}<div className="panel table-panel"><table><thead><tr><th>Phương tiện</th><th>Biển số</th><th>Loại xe</th><th>Tài xế</th><th /></tr></thead><tbody>{filtered.map((v) => <tr key={v.id}><td><strong>{v.displayName}</strong></td><td><span className="plate">{v.licensePlate || "—"}</span></td><td>{v.vehicleType || "—"}</td><td>{userName(v.userId)}</td><td><button className="text-button danger" onClick={() => remove(v.id)}>Xóa</button></td></tr>)}</tbody></table>{!filtered.length && <Empty text="Không tìm thấy phương tiện" />}</div>{adding && <VehicleDialog users={users} close={() => setAdding(false)} saved={async () => { setAdding(false); await reload(); }} />}</div>;
}

function VehicleDialog({ users, close, saved }: { users: User[]; close: () => void; saved: () => Promise<void> }) {
  const [form, setForm] = useState({ userId: users[0]?.id || "", displayName: "", licensePlate: "", vehicleType: "car" as Vehicle["vehicleType"] }); const [error, setError] = useState("");
  async function submit(e: FormEvent) { e.preventDefault(); try { await api.createVehicle({ ...form, licensePlate: form.licensePlate || null }); await saved(); } catch (reason) { setError(reason instanceof Error ? reason.message : "Không thể thêm phương tiện"); } }
  return <Modal title="Thêm phương tiện" close={close}><form className="dialog-form" onSubmit={submit}><label>Tài xế<select value={form.userId} onChange={(e) => setForm({ ...form, userId: e.target.value })}>{users.map((u) => <option value={u.id} key={u.id}>{u.fullName}</option>)}</select></label><label>Tên phương tiện<input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} required /></label><label>Biển số<input value={form.licensePlate} onChange={(e) => setForm({ ...form, licensePlate: e.target.value })} /></label><label>Loại xe<select value={form.vehicleType || "car"} onChange={(e) => setForm({ ...form, vehicleType: e.target.value as Vehicle["vehicleType"] })}><option value="car">Ô tô</option><option value="motorbike">Xe máy</option><option value="truck">Xe tải</option><option value="bus">Xe khách</option></select></label>{error && <div className="form-error">{error}</div>}<div className="dialog-actions"><button type="button" className="secondary-button" onClick={close}>Hủy</button><button className="primary-button">Thêm mới</button></div></form></Modal>;
}

function SessionsPage({ items, users, devices, vehicles }: { items: MonitoringSession[]; users: User[]; devices: Device[]; vehicles: Vehicle[] }) {
  const [search, setSearch] = useState(""); const name = (id: string) => users.find((u) => u.id === id)?.fullName || id.slice(0, 8); const device = (id: string) => devices.find((d) => d.id === id)?.deviceCode || id.slice(0, 8); const plate = (id: string | null) => vehicles.find((v) => v.id === id)?.licensePlate || "—";
  const filtered = items.filter((s) => `${name(s.userId)} ${device(s.deviceId)} ${plate(s.vehicleId)}`.toLowerCase().includes(search.toLowerCase()));
  return <div className="page-stack"><Toolbar title="Phiên giám sát" description="Theo dõi các phiên được mở từ ứng dụng hoặc thiết bị." search={search} setSearch={setSearch} /><div className="panel table-panel"><table><thead><tr><th>Tài xế</th><th>Thiết bị</th><th>Phương tiện</th><th>Bắt đầu</th><th>Kết thúc</th><th>Trạng thái</th></tr></thead><tbody>{filtered.map((s) => <tr key={s.id}><td><strong>{name(s.userId)}</strong></td><td>{device(s.deviceId)}</td><td>{plate(s.vehicleId)}</td><td>{formatDate(s.startedAt)}</td><td>{formatDate(s.endedAt)}</td><td><Badge tone={s.status === "active" ? "green" : "gray"}>{s.status}</Badge></td></tr>)}</tbody></table>{!filtered.length && <Empty text="Chưa có phiên giám sát" />}</div></div>;
}

function AlertsPage({ data, open, reload }: { data: DataState; open: (event: DrowsinessEvent) => void; reload: () => Promise<void> }) {
  const [search, setSearch] = useState(""); const [status, setStatus] = useState(""); const sessions = new Map(data.sessions.map((s) => [s.id, s])); const name = (id?: string) => data.users.find((u) => u.id === id)?.fullName || "—";
  const filtered = data.events.filter((e) => (!status || e.status === status) && `${e.eventType} ${name(sessions.get(e.sessionId)?.userId)}`.toLowerCase().includes(search.toLowerCase()));
  async function download() {
    try {
      const blob = await api.exportReport();
      const url = URL.createObjectURL(blob);
      try {
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = "drowsiness-events.csv";
        anchor.click();
      } finally {
        URL.revokeObjectURL(url);
      }
    } catch (reason) {
      window.alert(reason instanceof Error ? reason.message : "Không thể xuất báo cáo");
    }
  }
  return <div className="page-stack"><Toolbar title="Cảnh báo buồn ngủ" description="Xem ảnh minh chứng và xử lý các sự kiện được gửi về." search={search} setSearch={setSearch} action={<button className="secondary-button" onClick={download}><Download />Xuất CSV</button>} /><div className="filter-row"><select value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Tất cả trạng thái</option><option value="NEW">Mới</option><option value="ACKNOWLEDGED">Đã xác nhận</option><option value="RESOLVED">Đã xử lý</option></select><button className="icon-button" onClick={reload}><RefreshCw /></button></div><div className="panel table-panel"><table><thead><tr><th>Thời gian</th><th>Tài xế</th><th>Loại</th><th>EAR</th><th>Độ tin cậy</th><th>Nhắm mắt</th><th>Trạng thái</th></tr></thead><tbody>{filtered.map((e) => { const session = sessions.get(e.sessionId); return <tr key={e.id} className="clickable" onClick={() => open(e)}><td>{formatDate(e.occurredAt)}</td><td><strong>{name(session?.userId)}</strong></td><td>{e.eventType}</td><td>{e.ear ?? "—"}</td><td>{e.confidence == null ? "—" : `${Math.round(e.confidence * 100)}%`}</td><td>{e.closedDurationMs == null ? "—" : `${e.closedDurationMs} ms`}</td><td><Badge tone={e.status === "NEW" ? "red" : e.status === "ACKNOWLEDGED" ? "amber" : "green"}>{e.status}</Badge></td></tr>; })}</tbody></table>{!filtered.length && <Empty text="Không tìm thấy cảnh báo" />}</div></div>;
}

function EventDetail({ event, close, saved }: { event: DrowsinessEvent; close: () => void; saved: () => Promise<void> }) {
  const [status, setStatus] = useState(event.status); const [note, setNote] = useState(event.note || ""); const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  async function submit() { setBusy(true); try { await api.updateEventStatus(event.id, status, note); await saved(); close(); } catch (e) { setError(e instanceof Error ? e.message : "Không thể xử lý cảnh báo"); } finally { setBusy(false); } }
  return <Modal title="Chi tiết cảnh báo" close={close} wide><div className="event-detail">{event.imageUrl ? <img src={event.imageUrl} alt="Ảnh minh chứng cảnh báo" /> : <div className="evidence-empty"><Camera />Không có ảnh minh chứng</div>}<div className="event-metrics"><div><small>Loại</small><strong>{event.eventType}</strong></div><div><small>EAR</small><strong>{event.ear ?? "—"}</strong></div><div><small>Confidence</small><strong>{event.confidence == null ? "—" : `${Math.round(event.confidence * 100)}%`}</strong></div><div><small>Nhắm mắt</small><strong>{event.closedDurationMs != null ? `${event.closedDurationMs} ms` : "—"}</strong></div></div><label htmlFor="event-status">Trạng thái<select id="event-status" aria-label="Trạng thái" value={status} onChange={(e) => setStatus(e.target.value as DrowsinessEvent["status"])}><option value="NEW">Mới</option><option value="ACKNOWLEDGED">Đã xác nhận</option><option value="RESOLVED">Đã xử lý</option></select></label><label>Ghi chú<textarea value={note} onChange={(e) => setNote(e.target.value)} rows={3} /></label>{error && <div className="form-error">{error}</div>}<button className="primary-button" onClick={submit} disabled={busy}>{busy ? "Đang lưu..." : "Lưu xử lý"}</button></div></Modal>;
}

function Modal({ title, close, children, wide = false }: { title: string; close: () => void; children: React.ReactNode; wide?: boolean }) { return <div className="modal-backdrop" onMouseDown={close}><section role="dialog" aria-modal="true" aria-label={title} className={`modal ${wide ? "modal-wide" : ""}`} onMouseDown={(e) => e.stopPropagation()}><header><h2>{title}</h2><button aria-label="Đóng hộp thoại" onClick={close}><X /></button></header>{children}</section></div>; }

function AppShell({ logout }: { logout: () => void }) {
  const [view, setView] = useState<View>("dashboard"); const [data, setData] = useState<DataState>(EMPTY_DATA); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [menu, setMenu] = useState(false); const [dark, setDark] = useState(true); const [event, setEvent] = useState<DrowsinessEvent | null>(null); const [searchDraft, setSearchDraft] = useState(""); const [globalSearch, setGlobalSearch] = useState("");
  const load = useCallback(async () => { setLoading(true); setError(""); try { const [summary, trend, recentAlerts, users, devices, vehicles, sessions, events] = await Promise.all([api.dashboard(), api.alertTrend(), api.recentAlerts(), api.users(), api.devices(), api.vehicles(), api.sessions(), api.events()]); setData({ summary, trend, recentAlerts, users, devices, vehicles, sessions, events: events.items }); } catch (reason) { setError(reason instanceof Error ? reason.message : "Không thể tải dữ liệu"); } finally { setLoading(false); } }, []);
  useEffect(() => { void load(); }, [load]); useEffect(() => { document.documentElement.dataset.theme = dark ? "dark" : "light"; }, [dark]);
  const content = useMemo(() => { if (view === "dashboard") return <Dashboard data={data} openAlert={(recent) => setEvent(data.events.find(item => item.id === recent.id) || null)} />; if (view === "devices") return <DevicesPage items={data.devices} reload={load} />; if (view === "bindings") return <BindingsPage users={data.users} devices={data.devices} />; if (view === "users") return <UsersPage items={data.users} reload={load} />; if (view === "vehicles") return <VehiclesPage items={data.vehicles} users={data.users} reload={load} />; if (view === "sessions") return <SessionsPage items={data.sessions} users={data.users} devices={data.devices} vehicles={data.vehicles} />; if (view === "settings") return <SettingsPage devices={data.devices} />; if (view === "health") return <HealthPage devices={data.devices} />; if (view === "audit") return <AuditPage />; if (view === "search") return <GlobalSearchPage term={globalSearch} />; return <AlertsPage data={data} open={setEvent} reload={load} />; }, [view, data, load, globalSearch]);
  function submitGlobalSearch(e: FormEvent) { e.preventDefault(); const value = searchDraft.trim(); if (!value) return; setGlobalSearch(value); setView("search"); }
  return <div className="app-shell"><Sidebar view={view} setView={setView} open={menu} close={() => setMenu(false)} logout={logout} alerts={data.summary?.unhandledAlerts || 0} /><div className="main-column"><header className="topbar"><button aria-label="Mở menu" className="menu-button" onClick={() => setMenu(true)}><Menu /></button><div className="topbar-breadcrumb"><span>DrowsyGuard</span><ChevronRight /><strong>{NAV.find((n) => n.id === view)?.label}</strong></div><form className="topbar-search" onSubmit={submitGlobalSearch}><Search /><input aria-label="Tìm kiếm toàn hệ thống" value={searchDraft} onChange={e => setSearchDraft(e.target.value)} placeholder="Tìm tài xế, thiết bị, phương tiện..." /><button aria-label="Thực hiện tìm kiếm">Tìm</button></form><div className="topbar-actions"><button aria-label="Đổi giao diện sáng tối" onClick={() => setDark((v) => !v)}>{dark ? <Sun /> : <Moon />}</button><button><Bell /><span className="notification-dot" /></button><span className="admin-avatar"><CircleUserRound /></span></div></header><main className="content">{error && <div className="connection-error"><WifiOff /><div><strong>Không thể tải dữ liệu Backend</strong><p>{error}</p></div><button onClick={load}>Thử lại</button></div>}{loading ? <div className="loading-screen"><LoaderCircle className="spin" /><span>Đang đồng bộ dữ liệu...</span></div> : content}</main></div>{event && <EventDetail event={event} close={() => setEvent(null)} saved={load} />}</div>;
}

export default function App() {
  const [authenticated, setAuthenticated] = useState(api.hasToken());
  useEffect(() => { const unauthorized = () => setAuthenticated(false); window.addEventListener("driverguard:unauthorized", unauthorized); return () => window.removeEventListener("driverguard:unauthorized", unauthorized); }, []);
  if (!authenticated) return <Login onSuccess={() => setAuthenticated(true)} />;
  return <AppShell logout={() => { api.logout(); setAuthenticated(false); }} />;
}
