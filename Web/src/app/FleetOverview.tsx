import { AlertTriangle, BarChart3, ClipboardList, MapPin, Package, Truck } from 'lucide-react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Alert, Leave, Order, Trip } from '../api/dispatch';
import { hasCoordinates, LocationMap } from './LocationPanel';

export default function FleetOverview({ orders, trips, leaves, alerts, driverName, openAlerts, openTrips }: {
  orders: Order[]; trips: Trip[]; leaves: Leave[]; alerts: Alert[]; driverName: (id: string) => string;
  openAlerts: () => void; openTrips: () => void;
}) {
  const active = trips.filter(t => !['completed', 'completed_issues', 'cancelled'].includes(t.status));
  const stats = [
    { label: 'Đơn chờ xếp chuyến', value: orders.filter(o => o.status === 'ready').length, note: 'Đơn vận chuyển sẵn sàng', icon: Package, tone: 'blue' },
    { label: 'Chuyến đang thực hiện', value: trips.filter(t => t.status === 'in_progress').length, note: `${active.length} chuyến chưa kết thúc`, icon: Truck, tone: 'green' },
    { label: 'Cần điều phối lại', value: trips.filter(t => ['declined', 'needs_reassignment'].includes(t.status)).length, note: `${leaves.filter(l => l.status === 'pending').length} đơn nghỉ chờ duyệt`, icon: ClipboardList, tone: 'purple' },
    { label: 'Cảnh báo chưa xử lý', value: alerts.filter(a => !a.status || a.status === 'NEW').length, note: 'Sự kiện quan sát mắt đã đồng bộ', icon: AlertTriangle, tone: 'red' },
  ];
  const trend = Array.from({ length: 7 }, (_, index) => {
    const start = new Date(); start.setHours(0, 0, 0, 0); start.setDate(start.getDate() - 6 + index);
    const end = new Date(start); end.setDate(end.getDate() + 1);
    return { date: start.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' }), count: alerts.filter(a => a.timestamp >= +start && a.timestamp < +end).length };
  });
  const recent = [...alerts].sort((a, b) => b.timestamp - a.timestamp).slice(0, 6);
  const points = alerts.filter((a): a is Alert & { latitude: number; longitude: number } => hasCoordinates(a)).map(a => ({ id: a.id, latitude: a.latitude, longitude: a.longitude,
    label: `${driverName(a.userId)} · ${new Date(a.timestamp).toLocaleString('vi-VN')}`, alert: true }));
  return <>
    <section className="stats-grid">{stats.map(({ label, value, note, icon: Icon, tone }) => <article className="stat-card" key={label}><span className={`stat-icon ${tone}`}><Icon /></span><div><small>{label}</small><strong>{value}</strong><p>{note}</p></div></article>)}</section>
    <article className="panel"><div className="panel-heading"><div><h2>Vị trí cảnh báo tài xế</h2><p>Các vị trí đã ghi nhận; không phải vị trí trực tiếp của xe.</p></div><MapPin /></div><LocationMap points={points} route={false} />{!points.length && <p className="overview-map-note">Chưa có cảnh báo kèm tọa độ GPS. Vị trí sẽ hiện khi ứng dụng đồng bộ dữ liệu.</p>}</article>
    <section className="dashboard-grid">
      <article className="panel chart-panel"><div className="panel-heading"><div><h2>Xu hướng cảnh báo</h2><p>7 ngày gần nhất</p></div><BarChart3 /></div><div className="chart-wrap"><ResponsiveContainer width="100%" height="100%"><AreaChart data={trend}><defs><linearGradient id="fleetTrend" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--blue)" stopOpacity={0.4} /><stop offset="100%" stopColor="var(--blue)" stopOpacity={0} /></linearGradient></defs><CartesianGrid strokeDasharray="3 3" stroke="var(--line)" /><XAxis dataKey="date" tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} /><YAxis allowDecimals={false} tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} /><Tooltip contentStyle={{ background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 10 }} /><Area type="monotone" dataKey="count" name="Cảnh báo" stroke="var(--blue)" fill="url(#fleetTrend)" strokeWidth={2} /></AreaChart></ResponsiveContainer></div></article>
      <article className="panel"><div className="panel-heading"><div><h2>Cảnh báo gần nhất</h2><p>Sự kiện đã nhận từ ứng dụng</p></div><button className="text-button" onClick={openAlerts}>Xem tất cả</button></div><div className="activity-list">{recent.map(a => <button key={a.id} onClick={openAlerts}><span className="severity warning"><AlertTriangle /></span><div><strong>{driverName(a.userId)}</strong><small>{new Date(a.timestamp).toLocaleString('vi-VN')}</small></div><span className={`badge badge-${a.status === 'RESOLVED' ? 'green' : 'amber'}`}>{a.status === 'RESOLVED' ? 'Đã xử lý' : 'Cần xem'}</span></button>)}{!recent.length && <div className="empty-state"><AlertTriangle /><span>Chưa có cảnh báo</span></div>}</div></article>
    </section>
    <article className="panel"><div className="panel-heading"><div><h2>Điều phối vận tải</h2><p>{active.length} chuyến đang chờ nhận hoặc thực hiện</p></div><button className="primary-button" onClick={openTrips}><Truck />Xem chuyến xe</button></div></article>
  </>;
}
