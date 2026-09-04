import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { Activity, AlertTriangle, Link2, LoaderCircle, Search, Settings2, ShieldCheck, Unlink, X } from "lucide-react";
import {
  api, AuditLog, DetectionSetting, Device, DeviceBinding, DeviceHealth, SearchResult, User,
} from "../api/client";

function date(value?: string | null) {
  return value ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—";
}
function Empty({ text }: { text: string }) { return <div className="empty-state"><Search /><span>{text}</span></div>; }
function ErrorBox({ text }: { text: string }) { return text ? <div className="inline-error"><AlertTriangle />{text}</div> : null; }
function Loading() { return <div className="loading-screen"><LoaderCircle className="spin" />Đang tải dữ liệu...</div>; }
function Heading({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="page-heading"><div><p>QUẢN TRỊ</p><h1>{title}</h1><span>{description}</span></div>{action}</div>;
}
function Modal({ title, close, children }: { title: string; close: () => void; children: ReactNode }) {
  return <div className="modal-backdrop" onMouseDown={close}><section role="dialog" aria-modal="true" aria-label={title} className="modal modal-wide" onMouseDown={e => e.stopPropagation()}><header><h2>{title}</h2><button aria-label="Đóng hộp thoại" onClick={close}><X /></button></header>{children}</section></div>;
}

export function BindingsPage({ users, devices }: { users: User[]; devices: Device[] }) {
  const [items, setItems] = useState<DeviceBinding[]>([]); const [loading, setLoading] = useState(true);
  const [error, setError] = useState(""); const [adding, setAdding] = useState(false); const [busy, setBusy] = useState("");
  const load = async () => { setLoading(true); setError(""); try { setItems(await api.bindings()); } catch (e) { setError(e instanceof Error ? e.message : "Không thể tải gán thiết bị"); } finally { setLoading(false); } };
  useEffect(() => { void load(); }, []);
  const userName = (id: string) => users.find(u => u.id === id)?.fullName || id.slice(0, 8);
  const deviceName = (id: string) => devices.find(d => d.id === id)?.deviceName || id.slice(0, 8);
  async function unbind(item: DeviceBinding) { if (!confirm(`Hủy gán ${deviceName(item.deviceId)}?`)) return; setBusy(item.id); try { await api.unbindDevice(item.id); await load(); } catch (e) { setError(e instanceof Error ? e.message : "Không thể hủy gán"); } finally { setBusy(""); } }
  return <div className="page-stack"><Heading title="Gán thiết bị" description="Quản lý quan hệ thiết bị – tài xế theo thời gian." action={<button className="primary-button" disabled={!users.length || !devices.length} onClick={() => setAdding(true)}><Link2 />Gán thiết bị</button>} /><ErrorBox text={error} />{loading ? <Loading /> : <div className="panel table-panel"><table><thead><tr><th>Thiết bị</th><th>Tài xế</th><th>Ngày gán</th><th>Ngày hủy</th><th>Trạng thái</th><th /></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{deviceName(item.deviceId)}</strong></td><td>{userName(item.userId)}</td><td>{date(item.boundAt)}</td><td>{date(item.unboundAt)}</td><td><span className={`badge badge-${item.status === "active" ? "green" : "gray"}`}>{item.status}</span></td><td>{item.status === "active" && <button className="text-button danger" disabled={busy === item.id} onClick={() => unbind(item)}><Unlink /> Hủy gán</button>}</td></tr>)}</tbody></table>{!items.length && <Empty text="Chưa có lịch sử gán thiết bị" />}</div>}{adding && <BindingDialog users={users} devices={devices} close={() => setAdding(false)} saved={async () => { setAdding(false); await load(); }} />}</div>;
}
function BindingDialog({ users, devices, close, saved }: { users: User[]; devices: Device[]; close: () => void; saved: () => Promise<void> }) {
  const [userId, setUserId] = useState(users[0]?.id || ""); const [deviceId, setDeviceId] = useState(devices[0]?.id || "");
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) { e.preventDefault(); setBusy(true); setError(""); try { await api.createBinding({ userId, deviceId }); await saved(); } catch (reason) { setError(reason instanceof Error ? reason.message : "Không thể gán thiết bị"); } finally { setBusy(false); } }
  return <Modal title="Gán thiết bị cho tài xế" close={close}><form className="dialog-form" onSubmit={submit}><label>Tài xế<select value={userId} onChange={e => setUserId(e.target.value)}>{users.map(u => <option key={u.id} value={u.id}>{u.fullName}{u.phone ? ` – ${u.phone}` : ""}</option>)}</select></label><label>Thiết bị<select value={deviceId} onChange={e => setDeviceId(e.target.value)}>{devices.map(d => <option key={d.id} value={d.id}>{d.deviceName} – {d.deviceCode}</option>)}</select></label><p className="form-hint">Nếu thiết bị đang có chủ, Backend sẽ tự đóng lần gán cũ trước khi tạo lần gán mới.</p><ErrorBox text={error} /><div className="dialog-actions"><button type="button" className="secondary-button" onClick={close}>Hủy</button><button className="primary-button" disabled={busy}>{busy ? "Đang gán..." : "Xác nhận gán"}</button></div></form></Modal>;
}

export function SettingsPage({ devices }: { devices: Device[] }) {
  const [items, setItems] = useState<DetectionSetting[]>([]); const [selected, setSelected] = useState<string>("");
  const [form, setForm] = useState({ earThreshold: "0.25", confidenceThreshold: "0.8", closedDurationThresholdMs: "2000" });
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const [success, setSuccess] = useState("");
  const load = async () => { setLoading(true); try { setItems(await api.detectionSettings()); } catch (e) { setError(e instanceof Error ? e.message : "Không thể tải cấu hình"); } finally { setLoading(false); } };
  useEffect(() => { void load(); }, []);
  function choose(value: string) { setSelected(value); const item = items.find(s => (s.deviceId || "") === value); if (item) setForm({ earThreshold: String(item.earThreshold), confidenceThreshold: String(item.confidenceThreshold), closedDurationThresholdMs: String(item.closedDurationThresholdMs) }); }
  async function submit(e: FormEvent) { e.preventDefault(); setBusy(true); setError(""); setSuccess(""); try { await api.saveDetectionSettings({ deviceId: selected || null, earThreshold: Number(form.earThreshold), confidenceThreshold: Number(form.confidenceThreshold), closedDurationThresholdMs: Number(form.closedDurationThresholdMs) }); setSuccess("Đã lưu cấu hình nhận diện"); await load(); } catch (reason) { setError(reason instanceof Error ? reason.message : "Không thể lưu cấu hình"); } finally { setBusy(false); } }
  return <div className="page-stack"><Heading title="Cấu hình nhận diện" description="Thiết lập ngưỡng mặc định hoặc ghi đè riêng cho thiết bị." /><ErrorBox text={error} />{loading ? <Loading /> : <section className="management-grid"><form className="panel dialog-form" onSubmit={submit}><label>Phạm vi<select aria-label="Phạm vi cấu hình" value={selected} onChange={e => choose(e.target.value)}><option value="">Mặc định toàn hệ thống</option>{devices.map(d => <option key={d.id} value={d.id}>{d.deviceName} – {d.deviceCode}</option>)}</select></label><label>Ngưỡng EAR<input type="number" required min="0" max="1" step="0.01" value={form.earThreshold} onChange={e => setForm({ ...form, earThreshold: e.target.value })} /></label><label>Ngưỡng confidence<input type="number" required min="0" max="1" step="0.01" value={form.confidenceThreshold} onChange={e => setForm({ ...form, confidenceThreshold: e.target.value })} /></label><label>Thời gian nhắm mắt (ms)<input type="number" required min="100" step="100" value={form.closedDurationThresholdMs} onChange={e => setForm({ ...form, closedDurationThresholdMs: e.target.value })} /></label>{success && <div className="success-box"><ShieldCheck />{success}</div>}<button className="primary-button" disabled={busy}><Settings2 />{busy ? "Đang lưu..." : "Lưu cấu hình"}</button></form><div className="panel table-panel"><table><thead><tr><th>Phạm vi</th><th>EAR</th><th>Confidence</th><th>Nhắm mắt</th><th>Cập nhật</th></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{item.deviceId ? devices.find(d => d.id === item.deviceId)?.deviceName || item.deviceId.slice(0, 8) : "Mặc định"}</strong></td><td>{item.earThreshold}</td><td>{item.confidenceThreshold}</td><td>{item.closedDurationThresholdMs} ms</td><td>{date(item.updatedAt)}</td></tr>)}</tbody></table>{!items.length && <Empty text="Chưa có cấu hình nhận diện" />}</div></section>}</div>;
}

export function HealthPage({ devices }: { devices: Device[] }) {
  const [deviceId, setDeviceId] = useState(""); const [items, setItems] = useState<DeviceHealth[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const load = async (id = deviceId) => { setLoading(true); setError(""); try { setItems(await api.deviceHealth(id || undefined)); } catch (e) { setError(e instanceof Error ? e.message : "Không thể tải heartbeat"); } finally { setLoading(false); } };
  useEffect(() => { void load(""); }, []);
  const deviceName = (id: string) => devices.find(d => d.id === id)?.deviceName || id.slice(0, 8);
  async function filter(value: string) { setDeviceId(value); await load(value); }
  return <div className="page-stack"><Heading title="Tình trạng thiết bị" description="100 heartbeat gần nhất được thiết bị gửi về." /><div className="filter-row"><select aria-label="Lọc thiết bị" value={deviceId} onChange={e => void filter(e.target.value)}><option value="">Tất cả thiết bị</option>{devices.map(d => <option key={d.id} value={d.id}>{d.deviceName}</option>)}</select></div><ErrorBox text={error} />{loading ? <Loading /> : <div className="panel table-panel"><table><thead><tr><th>Thiết bị</th><th>Trạng thái</th><th>Heartbeat</th><th>Ghi nhận</th><th>Ghi chú</th></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{deviceName(item.deviceId)}</strong></td><td><span className={`badge badge-${item.status === "connected" ? "green" : "amber"}`}>{item.status}</span></td><td>{date(item.lastHeartbeatAt)}</td><td>{date(item.createdAt)}</td><td>{item.note || "—"}</td></tr>)}</tbody></table>{!items.length && <Empty text="Chưa có dữ liệu heartbeat" />}</div>}</div>;
}

export function AuditPage() {
  const [items, setItems] = useState<AuditLog[]>([]); const [table, setTable] = useState(""); const [selected, setSelected] = useState<AuditLog | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const load = async (target = table) => { setLoading(true); setError(""); try { setItems(await api.auditLogs({ target_table: target || undefined })); } catch (e) { setError(e instanceof Error ? e.message : "Không thể tải nhật ký"); } finally { setLoading(false); } };
  useEffect(() => { void load(""); }, []);
  const tables = useMemo(() => Array.from(new Set(items.map(item => item.targetTable))).sort(), [items]);
  return <div className="page-stack"><Heading title="Nhật ký quản trị" description="Theo dõi thao tác thay đổi dữ liệu của quản trị viên." /><div className="filter-row"><select aria-label="Lọc bảng dữ liệu" value={table} onChange={e => { setTable(e.target.value); void load(e.target.value); }}><option value="">Tất cả đối tượng</option>{tables.map(value => <option key={value}>{value}</option>)}</select></div><ErrorBox text={error} />{loading ? <Loading /> : <div className="panel table-panel"><table><thead><tr><th>Thời gian</th><th>Admin</th><th>Hành động</th><th>Đối tượng</th><th>Mã bản ghi</th><th /></tr></thead><tbody>{items.map(item => <tr key={item.id}><td>{date(item.createdAt)}</td><td>{item.adminId.slice(0, 8)}</td><td><strong>{item.action}</strong></td><td>{item.targetTable}</td><td>{item.targetId?.slice(0, 8) || "—"}</td><td><button className="text-button" onClick={() => setSelected(item)}>Chi tiết</button></td></tr>)}</tbody></table>{!items.length && <Empty text="Chưa có nhật ký quản trị" />}</div>}{selected && <Modal title="Chi tiết thay đổi" close={() => setSelected(null)}><div className="audit-detail"><h3>Trước thay đổi</h3><pre>{JSON.stringify(selected.beforeValue, null, 2) || "Không có"}</pre><h3>Sau thay đổi</h3><pre>{JSON.stringify(selected.afterValue, null, 2) || "Không có"}</pre></div></Modal>}</div>;
}

export function GlobalSearchPage({ term }: { term: string }) {
  const [items, setItems] = useState<SearchResult[]>([]); const [loading, setLoading] = useState(false); const [error, setError] = useState("");
  useEffect(() => {
    const value = term.trim();
    if (!value) { setItems([]); setError(""); return; }
    let active = true; setLoading(true); setError("");
    void api.search(value).then(results => { if (active) setItems(results); }).catch(reason => { if (active) setError(reason instanceof Error ? reason.message : "Không thể tìm kiếm"); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [term]);
  return <div className="page-stack"><Heading title="Kết quả tìm kiếm" description={term ? `Kết quả cho “${term}” trong tài xế, thiết bị và phương tiện.` : "Nhập từ khóa trên thanh phía trên để tìm kiếm toàn hệ thống."} /><ErrorBox text={error} />{loading ? <Loading /> : <div className="search-results">{items.map(item => <article className="panel search-result" key={`${item.type}-${item.id}`}><span className={`result-type result-${item.type}`}><Activity /></span><div><small>{item.type}</small><strong>{item.title}</strong><p>{item.subtitle || "Không có thông tin phụ"}</p></div></article>)}{term && !items.length && !error && <Empty text="Không tìm thấy kết quả phù hợp" />}{!term && <Empty text="Nhập từ khóa ở thanh tìm kiếm phía trên" />}</div>}</div>;
}
