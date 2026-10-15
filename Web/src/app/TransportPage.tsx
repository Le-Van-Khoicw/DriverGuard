import { FormEvent, useEffect, useState } from "react";
import type { User } from "../api/client";
import { createDelivery, defaultRules, Delivery, DeliveryStop, excludeAlert, listen, listenRules, millis, ranking, Reminder, Rules, SafetyAlert, saveRules, Score, sendReminder, settleDelivery } from "../api/transport";

const statuses: Record<Delivery["status"], string> = { assigned: "Đã phân công", accepted: "Đã nhận chuyến", in_progress: "Đang giao", completed: "Hoàn thành", cancelled: "Đã hủy" };
const date = (value: unknown) => millis(value) ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh" }).format(new Date(millis(value))) : "—";
export function TransportPage({ users, openGps }: { users: User[]; openGps: (id: string) => void }) {
  const [tab, setTab] = useState("deliveries");
  const [deliveries, setDeliveries] = useState<Delivery[]>([]), [alerts, setAlerts] = useState<SafetyAlert[]>([]), [messages, setMessages] = useState<Reminder[]>([]), [scores, setScores] = useState<Score[]>([]);
  const [rules, setRules] = useState<Rules>(defaultRules), [draftRules, setDraftRules] = useState<Rules>(defaultRules);
  const [errors, setErrors] = useState<Record<string, string>>({}), [busy, setBusy] = useState(false), [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState<"week" | "month">("week");
  const [title, setTitle] = useState(""), [userId, setUserId] = useState(""), [scheduled, setScheduled] = useState("");
  const [stops, setStops] = useState<DeliveryStop[]>([{ address: "", status: "pending", note: "" }]);
  const [recipient, setRecipient] = useState(""), [deliveryId, setDeliveryId] = useState(""), [text, setText] = useState("");
  const [filter, setFilter] = useState("all"), [search, setSearch] = useState("");
  useEffect(() => {
    const error = (source: string) => (reason: Error) => { setErrors(old => ({ ...old, [source]: reason.message })); setLoading(false); };
    const clear = (source: string) => setErrors(old => { const copy = { ...old }; delete copy[source]; return copy; });
    const unsubs = [
      listen<Delivery>("deliveries", items => { setDeliveries(items); clear("Chuyến hàng"); setLoading(false); }, error("Chuyến hàng")),
      listen<SafetyAlert>("alerts", items => { setAlerts(items); clear("Cảnh báo"); }, error("Cảnh báo")),
      listen<Reminder>("employeeMessages", items => { setMessages(items); clear("Tin nhắn"); }, error("Tin nhắn")),
      listen<Score>("deliveryScores", items => { setScores(items); clear("Điểm"); }, error("Điểm")),
      listenRules(value => { setRules(value); setDraftRules(value); clear("Cấu hình"); }, error("Cấu hình")),
    ];
    return () => unsubs.forEach(unsub => unsub());
  }, []);
  const name = (id: string) => users.find(user => user.id === id)?.fullName || id;
  const active = users.filter(user => user.role === "driver" && (user.accountStatus ?? (user.isActive ? "active" : "locked")) === "active");
  async function perform(action: () => Promise<unknown>, success: string) {
    setBusy(true); setNotice(""); setErrors(old => ({ ...old, action: "" }));
    try { await action(); setNotice(success); } catch (reason) { setErrors(old => ({ ...old, action: reason instanceof Error ? reason.message : "Thao tác thất bại" })); } finally { setBusy(false); }
  }
  function submitDelivery(event: FormEvent) {
    event.preventDefault();
    void perform(async () => {
      // datetime-local is interpreted explicitly in the company's Vietnam timezone.
      await createDelivery({ title, userId, scheduledAt: Date.parse(`${scheduled}:00+07:00`), stops }, users);
      setTitle(""); setStops([{ address: "", status: "pending", note: "" }]); setScheduled("");
    }, "Đã tạo và phân công chuyến hàng.");
  }
  const filtered = deliveries.filter(item => (filter === "all" || item.status === filter) && `${item.title} ${name(item.userId)}`.toLocaleLowerCase("vi").includes(search.toLocaleLowerCase("vi"))).sort((a, b) => millis(b.createdAt) - millis(a.createdAt));
  const board = ranking(scores.map(score => ({ ...score, completedAt: millis(score.completedAt) })), period);
  return <div className="page-stack">
    <div className="page-heading"><div><p>VẬN TẢI NỘI BỘ</p><h1>Điều hành vận tải</h1><span>Chuyến hàng, an toàn, nhắc nhở và thành tích nhân viên.</span></div></div>
    <nav className="filter-row" aria-label="Nghiệp vụ vận tải">{[["deliveries", "Chuyến hàng"], ["safety", "Theo dõi an toàn"], ["messages", "Nhắc nhở"], ["scores", "Điểm và xếp hạng"]].map(([id, label]) => <button className={tab === id ? "primary-button" : "secondary-button"} aria-pressed={tab === id} key={id} onClick={() => setTab(id)}>{label}</button>)}</nav>
    {Object.entries(errors).filter(([, message]) => message).map(([source, message]) => <div role="alert" className="inline-error" key={source}>{source}: {message}</div>)}
    {notice && <p role="status">{notice}</p>}
    {tab === "deliveries" && <>
      <div className="panel table-panel"><table><caption>Số chuyến theo nhân viên</caption><thead><tr><th>Nhân viên</th><th>Tổng chuyến</th><th>Đang thực hiện</th><th>Hoàn thành</th></tr></thead><tbody>{users.filter(user => user.role === "driver").map(user => { const assigned = deliveries.filter(item => item.userId === user.id); return <tr key={user.id}><td>{user.fullName}</td><td>{assigned.length}</td><td>{assigned.filter(item => ["assigned", "accepted", "in_progress"].includes(item.status)).length}</td><td>{assigned.filter(item => item.status === "completed").length}</td></tr>; })}</tbody></table></div>
      <form className="panel settings-form" onSubmit={submitDelivery}><h2>Tạo chuyến hàng</h2>
        <label>Tên chuyến<input required maxLength={200} value={title} onChange={e => setTitle(e.target.value)} /></label>
        <label>Nhân viên phụ trách<select required value={userId} onChange={e => setUserId(e.target.value)}><option value="">Chọn nhân viên hoạt động</option>{active.map(user => <option value={user.id} key={user.id}>{user.fullName} — {user.username}</option>)}</select></label>
        <label>Giờ xuất phát (Việt Nam)<input type="datetime-local" required value={scheduled} onChange={e => setScheduled(e.target.value)} /></label>
        {stops.map((stop, index) => <fieldset key={index}><legend>Điểm giao {index + 1}</legend><label>Địa chỉ<input required maxLength={500} value={stop.address} onChange={e => setStops(stops.map((s, i) => i === index ? { ...s, address: e.target.value } : s))} /></label><label>Ghi chú<input maxLength={1000} value={stop.note} onChange={e => setStops(stops.map((s, i) => i === index ? { ...s, note: e.target.value } : s))} /></label><button type="button" className="text-button danger" disabled={stops.length === 1} onClick={() => setStops(stops.filter((_, i) => i !== index))}>Xóa điểm giao</button></fieldset>)}
        <button type="button" className="secondary-button" onClick={() => setStops([...stops, { address: "", status: "pending", note: "" }])}>Thêm điểm giao</button><button className="primary-button" disabled={busy || !active.length}>Tạo và phân công</button>
      </form>
      <div className="filter-row"><label>Tìm chuyến<input value={search} onChange={e => setSearch(e.target.value)} /></label><label>Trạng thái<select value={filter} onChange={e => setFilter(e.target.value)}><option value="all">Tất cả</option>{Object.entries(statuses).map(([id, label]) => <option key={id} value={id}>{label}</option>)}</select></label></div>
      {loading && <p role="status">Đang tải chuyến hàng...</p>}
      {filtered.map(item => <article className="panel" key={item.id}><h2>{item.title}</h2><p>{name(item.userId)} · {statuses[item.status]} · {date(item.scheduledAt)}</p><p>Tiến độ: {item.stops.filter(stop => stop.status === "delivered").length}/{item.stops.length} điểm giao</p><ol>{item.stops.map((stop, i) => <li key={i}>{stop.address} — {stop.status === "delivered" ? "Đã giao" : stop.status === "failed" ? "Giao thất bại" : "Chưa giao"}{stop.note && <small> · {stop.note}</small>}</li>)}</ol><button className="text-button" onClick={() => openGps(item.userId)}>Xem GPS nhân viên</button></article>)}
      {!loading && !filtered.length && <p className="empty-state">Chưa có chuyến hàng phù hợp.</p>}
    </>}
    {tab === "safety" && <>
      <p>Vượt mức khi số cảnh báo chưa loại trừ lớn hơn {rules.allowedAlerts} lần/chuyến. Cảnh báo chưa có mã chuyến được hiển thị riêng, chưa dùng để chấm điểm.</p>
      {deliveries.map(item => { const count = alerts.filter(alert => alert.deliveryId === item.id && !alert.excluded).length; return <article className="panel" key={item.id}><strong>{item.title} — {name(item.userId)}</strong><p className={count > rules.allowedAlerts ? "inline-error" : ""}>{count} cảnh báo {count > rules.allowedAlerts ? "· Vượt mức quy định" : ""}</p><button className="text-button" onClick={() => openGps(item.userId)}>Xem GPS</button></article>; })}
      {[...alerts].sort((a, b) => millis(b.timestamp) - millis(a.timestamp)).map(alert => <article className="panel" key={alert.id}><strong>{name(alert.userId)}</strong><p>{date(alert.timestamp)} · {deliveries.find(item => item.id === alert.deliveryId)?.title || "Chưa gắn chuyến giao hàng"}</p>{alert.imageUrl && /^https?:\/\//i.test(alert.imageUrl) ? <a href={alert.imageUrl} target="_blank" rel="noreferrer"><img src={alert.imageUrl} alt="Ảnh sự kiện cảnh báo" loading="lazy" style={{ maxWidth: "100%", maxHeight: 240 }} /></a> : <p>Chưa có ảnh sự kiện.</p>}<p>{typeof alert.latitude === "number" && typeof alert.longitude === "number" ? `GPS: ${alert.latitude}, ${alert.longitude}` : "Chưa có tọa độ"}</p><button className="text-button" onClick={() => openGps(alert.userId)}>Xem GPS nhân viên</button><button className="text-button" disabled={busy} onClick={() => void perform(() => excludeAlert(alert.id, !alert.excluded), "Đã cập nhật đánh giá cảnh báo. Điểm đã chốt không tự thay đổi.")}>{alert.excluded ? "Khôi phục cảnh báo" : "Loại cảnh báo sai"}</button>{alert.excluded && <span className="badge badge-gray">Đã loại trừ</span>}</article>)}
      {!alerts.length && <p className="empty-state">Chưa có cảnh báo.</p>}
    </>}
    {tab === "messages" && <>
      <form className="panel settings-form" onSubmit={event => { event.preventDefault(); void perform(async () => { await sendReminder(recipient, deliveryId, text); setText(""); }, "Đã lưu nhắc nhở gửi nhân viên."); }}><h2>Gửi nhắc nhở</h2><label>Nhân viên<select required value={recipient} onChange={event => { setRecipient(event.target.value); setDeliveryId(""); }}><option value="">Chọn người nhận</option>{users.filter(user => user.role === "driver").map(user => <option key={user.id} value={user.id}>{user.fullName}</option>)}</select></label><label>Chuyến liên quan<select value={deliveryId} onChange={event => setDeliveryId(event.target.value)}><option value="">Không gắn chuyến</option>{deliveries.filter(item => item.userId === recipient).map(item => <option key={item.id} value={item.id}>{item.title}</option>)}</select></label><label>Nội dung<textarea required maxLength={2000} value={text} onChange={event => setText(event.target.value)} /></label><button disabled={busy || !text.trim()} className="primary-button">Gửi nhắc nhở</button></form>
      {[...messages].sort((a, b) => millis(b.createdAt) - millis(a.createdAt)).map(message => <article className="panel" key={message.id}><strong>{name(message.userId)}</strong><p style={{ whiteSpace: "pre-wrap" }}>{message.text}</p><small>{date(message.createdAt)} · {message.readAt ? `Đã đọc ${date(message.readAt)}` : "Chưa xác nhận đã đọc"}</small></article>)}{!messages.length && <p className="empty-state">Chưa có nhắc nhở.</p>}
    </>}
    {tab === "scores" && <>
      <form className="panel settings-form" onSubmit={event => { event.preventDefault(); void perform(() => saveRules(draftRules), "Đã lưu quy tắc cho các lần chốt điểm tiếp theo."); }}><h2>Quy tắc điểm và an toàn</h2>{([["allowedAlerts", "Số cảnh báo cho phép/chuyến"], ["completionPoints", "Điểm hoàn thành chuyến"], ["cleanBonus", "Thưởng thêm khi không vi phạm"], ["violationPenalty", "Điểm trừ mỗi vi phạm"]] as const).map(([key, label]) => <label key={key}>{label}<input type="number" min={0} max={100000} step={1} required value={draftRules[key]} onChange={event => setDraftRules({ ...draftRules, [key]: event.target.valueAsNumber })} /></label>)}<p>Điểm = điểm hoàn thành + thưởng chuyến sạch hoặc trừ theo số vi phạm; tối thiểu 0. Ngưỡng cảnh báo chỉ dùng để báo vượt mức.</p><button className="primary-button" disabled={busy}>Lưu quy tắc</button></form>
      <article className="panel"><h2>Chốt điểm chuyến hoàn thành</h2><p>Chỉ chốt sau khi tất cả điểm đã giao và máy chủ đã xác nhận số vi phạm.</p>{deliveries.filter(item => item.status === "completed").map(item => <p key={item.id}>{item.title} — {name(item.userId)} {scores.some(score => score.deliveryId === item.id) ? <span className="badge badge-green">Đã chốt</span> : <button disabled={busy} className="text-button" onClick={() => void perform(() => settleDelivery(item.id), "Đã chốt điểm chuyến.")}>Chốt điểm</button>}</p>)}</article>
      <label>Kỳ xếp hạng<select value={period} onChange={event => setPeriod(event.target.value as typeof period)}><option value="week">Tuần này (từ thứ Hai)</option><option value="month">Tháng này</option></select></label>
      <div className="panel table-panel"><table><caption>Bảng xếp hạng theo giờ Việt Nam</caption><thead><tr><th>Hạng</th><th>Nhân viên</th><th>Điểm</th><th>Chuyến</th></tr></thead><tbody>{board.map((entry, index) => <tr key={entry.userId}><td>{index + 1}</td><td>{name(entry.userId)}</td><td>{entry.points}</td><td>{entry.trips}</td></tr>)}</tbody></table>{!board.length && <p className="empty-state">Chưa có điểm đã chốt trong kỳ.</p>}</div>
      <article className="panel"><h2>Lịch sử điểm</h2>{[...scores].sort((a, b) => millis(b.completedAt) - millis(a.completedAt)).map(score => <p key={score.id}>{name(score.userId)} · {deliveries.find(item => item.id === score.deliveryId)?.title || score.deliveryId} · {score.points} điểm · {score.violations} vi phạm · {date(score.completedAt)}</p>)}</article>
    </>}
  </div>;
}
