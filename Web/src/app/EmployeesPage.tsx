import { useState } from "react";
import { Search, Users } from "lucide-react";
import { api, User } from "../api/client";
import { EmployeeStatus } from "../api/employees";

const labels: Record<EmployeeStatus, string> = { pending: "Chờ duyệt", active: "Hoạt động", locked: "Bị khóa", rejected: "Bị từ chối" };
const tones = { pending: "amber", active: "green", locked: "red", rejected: "red" };
const statusOf = (user: User): EmployeeStatus => user.accountStatus ?? (user.isActive ? "active" : "locked");

export function EmployeesPage({ items, reload }: { items: User[]; reload: () => Promise<void> }) {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<EmployeeStatus | "all">("all");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [rejecting, setRejecting] = useState<User | null>(null);
  const [reason, setReason] = useState("");
  const employees = items.filter(user => user.role !== "admin");
  const filtered = employees.filter(user => (filter === "all" || statusOf(user) === filter) &&
    `${user.fullName} ${user.username ?? ""} ${user.phone ?? ""} ${user.id}`.toLocaleLowerCase("vi").includes(search.trim().toLocaleLowerCase("vi")));

  async function change(user: User, status: EmployeeStatus, rejectReason?: string) {
    setBusy(user.id); setError(""); setNotice("");
    try {
      await api.updateUser(user.id, { accountStatus: status, ...(status === "rejected" ? { rejectReason: rejectReason?.trim() } : {}) });
      setRejecting(null); setReason("");
      await reload();
      setNotice(`${user.fullName}: ${labels[status]}.`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Không thể cập nhật nhân viên. Vui lòng thử lại.");
    } finally { setBusy(null); }
  }

  return <div className="page-stack">
    <div className="page-heading"><div><p>VẬN TẢI NỘI BỘ</p><h1>Quản lý nhân viên</h1><span>Duyệt đăng ký và quản lý trạng thái tài khoản nhân viên.</span></div><Users /></div>
    <section className="stats-grid" aria-label="Thống kê nhân viên">
      <article className="stat-card"><div><small>Tổng nhân viên</small><strong>{employees.length}</strong></div></article>
      {(["pending", "active", "locked", "rejected"] as const).map(status => <article className="stat-card" key={status}><div><small>{labels[status]}</small><strong>{employees.filter(user => statusOf(user) === status).length}</strong></div></article>)}
    </section>
    <div className="toolbar">
      <label className="search-box"><Search /><input aria-label="Tìm nhân viên" placeholder="Tên, email, điện thoại hoặc mã nhân viên..." value={search} onChange={event => setSearch(event.target.value)} /></label>
      <label>Trạng thái <select aria-label="Lọc trạng thái" value={filter} onChange={event => setFilter(event.target.value as typeof filter)}><option value="all">Tất cả</option>{Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    </div>
    {error && <div className="inline-error" role="alert">{error}</div>}
    {notice && <p role="status">{notice}</p>}
    {rejecting && <section className="panel" aria-label="Từ chối tài khoản"><form className="dialog-form" onSubmit={event => { event.preventDefault(); if (reason.trim()) void change(rejecting, "rejected", reason); }}>
      <h2>Từ chối tài khoản: {rejecting.fullName}</h2>
      <label>Lý do từ chối<textarea required maxLength={1000} value={reason} onChange={event => setReason(event.target.value)} /></label>
      <div className="dialog-actions"><button type="button" className="secondary-button" disabled={busy !== null} onClick={() => { setRejecting(null); setReason(""); }}>Hủy</button><button className="primary-button" disabled={busy !== null || !reason.trim()}>Xác nhận từ chối</button></div>
    </form></section>}
    <div className="panel table-panel"><table><thead><tr><th>Nhân viên</th><th>Email</th><th>Điện thoại</th><th>Ngày đăng ký</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
      {filtered.map(user => { const status = statusOf(user); return <tr key={user.id}>
        <td><strong>{user.fullName}</strong><small>{user.id}</small></td><td>{user.username || "Chưa có email"}</td><td>{user.phone || "—"}</td>
        <td>{Number.isNaN(Date.parse(user.createdAt)) ? "—" : new Intl.DateTimeFormat("vi-VN", { dateStyle: "short" }).format(new Date(user.createdAt))}</td>
        <td><span className={`badge badge-${tones[status]}`}>{labels[status]}</span>{status === "rejected" && <small>{user.rejectReason || "Chưa ghi lý do từ chối"}</small>}</td>
        <td>{status !== "active" && <button className="text-button" disabled={busy !== null} onClick={() => void change(user, "active")}>{busy === user.id ? "Đang lưu..." : status === "pending" ? "Duyệt tài khoản" : status === "rejected" ? "Duyệt lại" : "Mở khóa"}</button>}
          {(status === "locked" || status === "rejected") && <button className="text-button" disabled={busy !== null} onClick={() => void change(user, "pending")}>Chuyển về chờ duyệt</button>}
          {status === "pending" && <button className="text-button danger" disabled={busy !== null} onClick={() => { setRejecting(user); setReason(""); setError(""); }}>Từ chối</button>}
          {status === "active" && <button className="text-button danger" disabled={busy !== null} onClick={() => void change(user, "locked")}>{busy === user.id ? "Đang lưu..." : "Khóa"}</button>}</td>
      </tr>; })}
    </tbody></table>{filtered.length === 0 && <div className="empty-state"><Search /><span>{employees.length ? "Không tìm thấy nhân viên phù hợp" : "Chưa có nhân viên đăng ký"}</span></div>}</div>
  </div>;
}
