import { useState } from 'react';
import { Driver, FleetVehicle, Order } from '../api/dispatch';

export function driverAssignmentIssues(d: Driver, until: number, vehicleId: string) {
  const issues: string[] = [];
  if (d.accountStatus !== 'active') issues.push('Tài khoản chưa hoạt động');
  if (!d.qualificationStatus) issues.push('Chưa nộp hồ sơ trên app');
  else if (d.qualificationStatus === 'submitted') issues.push('Hồ sơ đang chờ quản lý duyệt');
  else if (d.qualificationStatus !== 'approved') issues.push('Hồ sơ cần bổ sung và duyệt lại');
  else {
    if (!(d.licenseExpiresAt! > until)) issues.push('GPLX hết hạn trước khi kết thúc chuyến');
    if (!(d.healthExpiresAt! > until)) issues.push('Hồ sơ sức khỏe hết hạn trước khi kết thúc chuyến');
    if (!d.approvedVehicleIds?.length) issues.push('Chưa xác nhận xe phù hợp hồ sơ');
    else if (vehicleId && !d.approvedVehicleIds.includes(vehicleId)) issues.push('Chưa được quản lý xác nhận lái xe đã chọn');
  }
  return issues;
}

export default function TripAssignmentForm({ orders, drivers, vehicles, busy, error, save, openProfile, admin }: {
  orders: Order[]; drivers: Driver[]; vehicles: FleetVehicle[]; busy: boolean; error: string; admin: boolean;
  save: (data: Record<string, unknown>) => void; openProfile: (uid: string) => void;
}) {
  const [vehicleId, setVehicleId] = useState(''), [driverId, setDriverId] = useState(''), [end, setEnd] = useState('');
  const [orderIds, setOrderIds] = useState<string[]>([]), [stopKeys, setStopKeys] = useState<string[]>([]);
  const until = Number.isFinite(Date.parse(end)) ? Math.max(Date.parse(end), Date.now()) : Date.now();
  const eligible = drivers.filter(d => !driverAssignmentIssues(d, until, vehicleId).length);
  const blocked = drivers.filter(d => driverAssignmentIssues(d, until, vehicleId).length);
  const selected = drivers.find(d => d.id === driverId);
  const selectedIssues = selected ? driverAssignmentIssues(selected, until, vehicleId) : [];
  function toggleOrder(id: string) {
    setOrderIds(v => v.includes(id) ? v.filter(x => x !== id) : [...v, id]);
    setStopKeys(v => v.includes(`${id}:pickup`) ? v.filter(x => !x.startsWith(`${id}:`)) : [...v, `${id}:pickup`, `${id}:delivery`]);
  }
  function moveStop(i: number, offset: number) { setStopKeys(v => { const next = [...v]; [next[i], next[i + offset]] = [next[i + offset], next[i]]; return next; }); }
  return <form onSubmit={e => { e.preventDefault(); if (!selected || selectedIssues.length) return; const f = Object.fromEntries(new FormData(e.currentTarget)); save({ ...f, driverId, vehicleId, orderIds, stopKeys, start: new Date(String(f.start)).getTime(), end: new Date(end).getTime() }); }}>
    <label>Tên chuyến / tuyến<input name="name" placeholder="VD: Bình Dương → Khánh Hòa" required /></label>
    <div className="log-form-grid"><label>Xe<select required value={vehicleId} onChange={e => setVehicleId(e.target.value)}><option value="">Chọn xe</option>{vehicles.filter(v => v.status === 'available').map(v => <option key={v.id} value={v.id}>{v.plate} · {v.capacityKg} kg</option>)}</select></label><label>Tài xế đủ hồ sơ<select required value={driverId} onChange={e => setDriverId(e.target.value)}><option value="">{eligible.length ? 'Chọn tài xế' : 'Chưa có tài xế đủ hồ sơ'}</option>{drivers.map(d => { const reasons = driverAssignmentIssues(d, until, vehicleId); return <option key={d.id} value={d.id} disabled={!!reasons.length}>{d.employeeCode || d.id.slice(0, 8)} · {d.displayName}{reasons.length ? ` — ${reasons[0]}` : ''}</option>; })}</select></label><label>Dự kiến khởi hành<input name="start" type="datetime-local" required /></label><label>Dự kiến kết thúc<input name="end" type="datetime-local" value={end} onChange={e => setEnd(e.target.value)} required /></label></div>
    {!!selectedIssues.length && <p className="log-error">{selected?.displayName}: {selectedIssues.join('; ')}</p>}
    {!!blocked.length && <details className="assignment-blockers" open={!eligible.length}><summary>{blocked.length} tài xế chưa thể phân công — xem lý do</summary>{blocked.map(d => <div className="assignment-blocker" key={d.id}><div><strong>{d.displayName} ({d.employeeCode || d.id.slice(0, 8)})</strong><small>{driverAssignmentIssues(d, until, vehicleId).join(' · ')}</small></div><button type="button" onClick={() => openProfile(d.id)}>{admin ? 'Xem/duyệt hồ sơ' : 'Xem hồ sơ'}</button></div>)}<p>{admin ? 'Quyền admin cho phép duyệt hồ sơ đã nộp. Tài xế chưa nộp cần mở app → Hồ sơ nghề nghiệp → nộp thông tin và giấy tờ trước.' : 'Nhắc tài xế nộp hồ sơ trên app và nhờ quản lý duyệt trước khi giao chuyến.'}</p></details>}
    <h3>Đơn sẵn sàng ({orderIds.length}/20)</h3>{orders.filter(o => o.status === 'ready' && !o.tripId).map(o => <label className="log-check" key={o.id}><input type="checkbox" checked={orderIds.includes(o.id)} disabled={!orderIds.includes(o.id) && orderIds.length >= 20} onChange={() => toggleOrder(o.id)} />{o.id.slice(0, 8)} · {o.customer} · {o.weightKg} kg · {o.deliveryAddress}</label>)}
    <p>Tổng hàng: {orders.filter(o => orderIds.includes(o.id)).reduce((s, o) => s + o.weightKg, 0)} kg. Lịch trùng, nghỉ và tải trọng được kiểm tra khi xác nhận.</p>
    <h3>Thứ tự điểm dừng</h3>{stopKeys.map((key, i) => { const [id, kind] = key.split(':'); const o = orders.find(o => o.id === id); return <div className="log-stop" key={key}><span>{i + 1}. {kind === 'pickup' ? 'Nhận' : 'Giao'} {id.slice(0, 8)} · {kind === 'pickup' ? o?.pickupAddress : o?.deliveryAddress}</span><button type="button" disabled={i === 0} onClick={() => moveStop(i, -1)}>↑</button><button type="button" disabled={i === stopKeys.length - 1} onClick={() => moveStop(i, 1)}>↓</button></div>; })}
    {error && <p role="alert" className="log-error">{error}</p>}
    {!eligible.length && <p className="account-help">Chưa thể giao chuyến vì chưa có tài xế đủ hồ sơ cho lựa chọn hiện tại.</p>}
    <button className="log-submit" disabled={busy || !orderIds.length || !driverId || !vehicleId || !!selectedIssues.length}>{busy ? 'Đang kiểm tra và giao…' : 'Xác nhận giao chuyến'}</button>
  </form>;
}
