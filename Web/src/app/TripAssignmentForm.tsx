import { useState, useMemo, FormEvent } from 'react';
import { Driver, FleetVehicle, Order, Trip, command } from '../api/dispatch';

export function driverAssignmentIssues(d: Driver, until: number, vehicleId: string) {
  const issues: string[] = [];
  if (d.accountStatus !== 'active') issues.push('Tài khoản chưa hoạt động');
  if (!d.qualificationStatus) issues.push('Chưa có hồ sơ được ghi nhận');
  else if (d.qualificationStatus === 'submitted') issues.push('Hồ sơ đang chờ duyệt');
  else if (d.qualificationStatus !== 'approved') issues.push('Hồ sơ cần bổ sung');
  else {
    if (!(d.licenseExpiresAt! > until)) issues.push('GPLX hết hạn');
    if (!(d.healthExpiresAt! > until)) issues.push('Hồ sơ sức khỏe hết hạn');
    if (!d.approvedVehicleIds?.length) issues.push('Chưa xác nhận xe phù hợp');
    else if (vehicleId && !d.approvedVehicleIds.includes(vehicleId)) issues.push('Chưa được duyệt lái xe này');
  }
  return issues;
}

export interface DirectStop {
  id: string;
  receiver: string;
  receiverPhone: string;
  address: string;
  cargoName: string;
  weightKg: number;
}

// ── HÀM CHUẨN HÓA VÀ SO SÁNH TUYẾN ĐƯỜNG GHÉP ĐƠN ──
function cleanStr(s: string): string {
  return (s || '').toLowerCase()
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

const REGION_KEYWORDS = [
  'song than', 'binh duong', 'di an', 'thuan an', 'thu duc',
  'ho chi minh', 'sai gon', 'quan 1', 'quan 2', 'quan 3', 'quan 7', 'quan 9', 'quan 12',
  'tan binh', 'binh tan', 'go vap', 'binh thanh',
  'nha trang', 'khanh hoa', 'cam ranh', 'ninh hoa',
  'da lat', 'lam dong', 'bao loc',
  'phan thiet', 'binh thuan',
  'vung tau', 'ba ria', 'phu my',
  'dong nai', 'bien hoa', 'long thanh',
  'can tho', 'long an', 'tien giang'
];

function extractRegions(addr: string): string[] {
  const c = cleanStr(addr);
  return REGION_KEYWORDS.filter(k => c.includes(k));
}

function extractShortLabel(addr: string): string {
  if (!addr) return '';
  if (addr.includes('http://') || addr.includes('https://')) {
    const match = addr.match(/(-?\d+\.\d+),\s*(-?\d+\.\d+)/);
    if (match) return `Tọa độ (${match[1].slice(0, 5)}, ${match[2].slice(0, 6)})`;
    return 'Vị trí Google Maps';
  }
  const parts = addr.split(',').map(s => s.trim()).filter(Boolean);
  if (parts.length >= 2) return parts.slice(-2).join(', ');
  return parts[0] || addr.slice(0, 25);
}

interface PoolGroup {
  id: string;
  routeName: string;
  orders: Order[];
  totalWeightKg: number;
}

export default function TripAssignmentForm({ orders, trips = [], drivers, vehicles, busy, error, save, openProfile, admin }: {
  orders: Order[]; trips?: Trip[]; drivers: Driver[]; vehicles: FleetVehicle[]; busy: boolean; error: string; admin: boolean;
  save: (data: Record<string, unknown>) => void; openProfile: (uid: string) => void;
}) {
  const now = new Date();
  const defaultStart = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  const defaultEnd = new Date(now.getTime() + 8 * 3600000 - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);

  const [vehicleId, setVehicleId] = useState('');
  const [driverId, setDriverId] = useState('');
  const [start, setStart] = useState(defaultStart);
  const [end, setEnd] = useState(defaultEnd);
  const [customName, setCustomName] = useState('');
  const [orderIds, setOrderIds] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [localError, setLocalError] = useState('');

  // Các đơn hàng sẵn sàng xếp chuyến
  const availableOrders = useMemo(() => orders.filter(o => o.status === 'ready' && !o.tripId), [orders]);

  // Nhập điểm giao trực tiếp (tùy chọn khi không dùng đơn sẵn)
  const [showManualStops, setShowManualStops] = useState(availableOrders.length === 0);
  const [pickupHub, setPickupHub] = useState('');
  const [directStops, setDirectStops] = useState<DirectStop[]>([]);

  // ── THUẬT TOÁN PHÂN TÍCH VÀ TÌM CÁC CỤM ĐƠN CÙNG TUYẾN ──
  const poolGroups = useMemo<PoolGroup[]>(() => {
    if (availableOrders.length < 2) return [];

    const groups: PoolGroup[] = [];
    const used = new Set<string>();

    for (let i = 0; i < availableOrders.length; i++) {
      const o1 = availableOrders[i];
      if (used.has(o1.id)) continue;

      const cluster: Order[] = [o1];
      const p1Regs = extractRegions(o1.pickupAddress);
      const d1Regs = extractRegions(o1.deliveryAddress);

      for (let j = i + 1; j < availableOrders.length; j++) {
        const o2 = availableOrders[j];
        if (used.has(o2.id)) continue;

        const p2Regs = extractRegions(o2.pickupAddress);
        const d2Regs = extractRegions(o2.deliveryAddress);

        const samePickup = (p1Regs.length > 0 && p2Regs.length > 0 && p1Regs.some(r => p2Regs.includes(r))) ||
          cleanStr(o1.pickupAddress).slice(0, 15) === cleanStr(o2.pickupAddress).slice(0, 15);

        const sameDelivery = (d1Regs.length > 0 && d2Regs.length > 0 && d1Regs.some(r => d2Regs.includes(r))) ||
          cleanStr(o1.deliveryAddress).slice(-15) === cleanStr(o2.deliveryAddress).slice(-15);

        if (samePickup && sameDelivery) {
          cluster.push(o2);
        }
      }

      if (cluster.length >= 2) {
        cluster.forEach(o => used.add(o.id));
        const originLabel = p1Regs[0] ? p1Regs[0].toUpperCase() : extractShortLabel(o1.pickupAddress);
        const destLabel = d1Regs[0] ? d1Regs[0].toUpperCase() : extractShortLabel(o1.deliveryAddress);
        const totalW = cluster.reduce((sum, o) => sum + (o.weightKg || 0), 0);
        groups.push({
          id: `pool_${o1.id}`,
          routeName: `${originLabel} → ${destLabel}`,
          orders: cluster,
          totalWeightKg: totalW
        });
      }
    }

    return groups;
  }, [availableOrders]);

  const until = Number.isFinite(Date.parse(end)) ? Math.max(Date.parse(end), Date.now()) : Date.now();
  const eligible = drivers.filter(d => !driverAssignmentIssues(d, until, vehicleId).length);
  const blocked = drivers.filter(d => driverAssignmentIssues(d, until, vehicleId).length);
  const selected = drivers.find(d => d.id === driverId);
  const selectedVehicle = vehicles.find(v => v.id === vehicleId);
  const selectedIssues = selected ? driverAssignmentIssues(selected, until, vehicleId) : [];

  // Tính tổng khối lượng hàng được chọn
  const selectedOrdersData = orders.filter(o => orderIds.includes(o.id));
  const validDirectStops = directStops.filter(s => s.address.trim().length > 0 && s.receiver.trim().length > 0);
  const totalWeight = selectedOrdersData.reduce((sum, o) => sum + (o.weightKg || 0), 0) +
    validDirectStops.reduce((sum, s) => sum + (s.weightKg || 0), 0);

  const isOverweight = selectedVehicle && totalWeight > selectedVehicle.capacityKg;

  const startTime = Date.parse(start);
  const endTime = Date.parse(end);
  const activeVehicleTrips = useMemo(() => (trips || []).filter(t => !['completed', 'completed_issues', 'cancelled'].includes(t.status)), [trips]);
  const activeDriverTrips = useMemo(() => (trips || []).filter(t => !['completed', 'completed_issues', 'cancelled', 'declined'].includes(t.status)), [trips]);

  const conflictingVehicleTrip = useMemo(() => {
    if (!vehicleId || !Number.isFinite(startTime) || !Number.isFinite(endTime)) return null;
    return activeVehicleTrips.find(t => t.vehicleId === vehicleId && t.start < endTime && startTime < t.end);
  }, [vehicleId, startTime, endTime, activeVehicleTrips]);

  const conflictingDriverTrip = useMemo(() => {
    if (!driverId || !Number.isFinite(startTime) || !Number.isFinite(endTime)) return null;
    return activeDriverTrips.find(t => t.driverId === driverId && t.start < endTime && startTime < t.end);
  }, [driverId, startTime, endTime, activeDriverTrips]);


  function onSelectDriver(dId: string) {
    setDriverId(dId);
    setLocalError('');
    const d = drivers.find(x => x.id === dId);
    if (d?.approvedVehicleIds?.length) {
      const matchedVeh = vehicles.find(v => d.approvedVehicleIds!.includes(v.id) && v.status === 'available');
      if (matchedVeh) setVehicleId(matchedVeh.id);
    }
  }

  function onSelectVehicle(vId: string) {
    setVehicleId(vId);
    setLocalError('');
    const matchedDriver = drivers.find(d => d.approvedVehicleIds?.includes(vId));
    if (matchedDriver) setDriverId(matchedDriver.id);
  }

  function toggleOrder(id: string) {
    setLocalError('');
    setOrderIds(v => {
      const next = v.includes(id) ? v.filter(x => x !== id) : [...v, id];
      // Tự động tìm xe phù hợp tải trọng nếu chưa chọn hoặc xe cũ bị thiếu tải
      const newOrders = orders.filter(o => next.includes(o.id));
      const newWeight = newOrders.reduce((sum, o) => sum + (o.weightKg || 0), 0);
      if (newWeight > 0 && (!selectedVehicle || selectedVehicle.capacityKg < newWeight)) {
        const fittingVeh = vehicles.find(veh => veh.status === 'available' && veh.capacityKg >= newWeight);
        if (fittingVeh) {
          setVehicleId(fittingVeh.id);
          const matchedDriver = drivers.find(d => d.approvedVehicleIds?.includes(fittingVeh.id));
          if (matchedDriver) setDriverId(matchedDriver.id);
        }
      }
      return next;
    });
  }

  // Bấm nút ghép cụm đơn cùng tuyến
  function applyPool(pg: PoolGroup) {
    setLocalError('');
    const poolIds = pg.orders.map(o => o.id);
    setOrderIds(poolIds);
    setCustomName(`Ghép ${pg.orders.length} đơn · ${pg.routeName}`);

    // Tự động chọn xe sẵn sàng đủ tải trọng cho cả cụm
    const fittingVeh = vehicles.find(v => v.status === 'available' && v.capacityKg >= pg.totalWeightKg);
    if (fittingVeh) {
      setVehicleId(fittingVeh.id);
      const matchedDriver = drivers.find(d => d.approvedVehicleIds?.includes(fittingVeh.id));
      if (matchedDriver) setDriverId(matchedDriver.id);
    }
  }

  function addDirectStop() {
    if (directStops.length >= 10) return;
    setDirectStops(v => [
      ...v,
      { id: `stop_${Date.now()}_${v.length + 1}`, receiver: '', receiverPhone: '', address: '', cargoName: 'Hàng hóa', weightKg: 0 }
    ]);
  }

  function updateDirectStop(index: number, field: keyof DirectStop, val: any) {
    setDirectStops(v => v.map((s, i) => i === index ? { ...s, [field]: val } : s));
  }

  function removeDirectStop(index: number) {
    setDirectStops(v => v.filter((_, i) => i !== index));
  }

  const totalStopsCount = orderIds.length + validDirectStops.length;
  const autoTripName = customName.trim() || (
    selectedOrdersData.length > 0
      ? `${selectedVehicle?.plate || 'Chuyến xe'} · ${selectedOrdersData.length > 1 ? `Ghép ${selectedOrdersData.length} đơn` : selectedOrdersData[0].customer}`
      : `${selectedVehicle?.plate || 'Chuyến xe'} · ${validDirectStops[0]?.address.split(',')[0].slice(0, 30) || 'Lộ trình mới'}`
  );

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!selected || selectedIssues.length) return;
    if (!vehicleId || !driverId) {
      setLocalError('Vui lòng chọn tài xế và phương tiện.');
      return;
    }

    if (isOverweight) {
      setLocalError(`Tổng khối lượng (${totalWeight} kg) vượt tải trọng của xe ${selectedVehicle?.plate} (${selectedVehicle?.capacityKg} kg). Vui lòng chọn xe tải lớn hơn.`);
      return;
    }

    setSubmitting(true);
    setLocalError('');

    try {
      const finalOrderIds = [...orderIds];

      // Nếu có thêm điểm giao trực tiếp thì tạo đơn ngầm
      for (let i = 0; i < validDirectStops.length; i++) {
        const s = validDirectStops[i];
        const res = await command('createOrder', {
          customer: s.receiver,
          customerPhone: s.receiverPhone || '0901234567',
          sender: 'Kho xuất phát',
          senderPhone: '0901112233',
          pickupAddress: pickupHub.trim() || 'Kho tổng xuất phát',
          receiver: s.receiver,
          receiverPhone: s.receiverPhone || '0901234567',
          deliveryAddress: s.address.trim(),
          items: [{ name: s.cargoName || 'Kiện hàng', quantity: 1, unit: 'kiện' }],
          weightKg: Number(s.weightKg) || 100,
          start: new Date(start).getTime(),
          end: new Date(end).getTime(),
          note: 'Tạo từ điều phối chuyến'
        });
        if (res && (res as any).id) {
          finalOrderIds.push((res as any).id);
        }
      }

      if (!finalOrderIds.length) {
        setLocalError('Vui lòng chọn ít nhất 1 đơn hàng hoặc thêm điểm giao.');
        setSubmitting(false);
        return;
      }

      // Tạo stopKeys logic chuẩn: lấy hàng trước -> giao hàng sau
      const finalStopKeys = [
        ...finalOrderIds.map(id => `${id}:pickup`),
        ...finalOrderIds.map(id => `${id}:delivery`)
      ];

      save({
        name: autoTripName,
        driverId,
        vehicleId,
        orderIds: finalOrderIds,
        stopKeys: finalStopKeys,
        start: new Date(start).getTime(),
        end: new Date(end).getTime()
      });
    } catch (err: any) {
      setLocalError(err?.message || 'Có lỗi khi tạo chuyến. Vui lòng thử lại.');
      setSubmitting(false);
    }
  }

  const isFormBusy = busy || submitting;

  return (
    <form className="trip-assign-clean-form" onSubmit={handleSubmit}>
      {/* 1. TÀI XẾ & PHƯƠNG TIỆN */}
      <fieldset style={{ border: '1px solid var(--line)', borderRadius: '12px', padding: '14px', marginBottom: '14px' }}>
        <legend style={{ fontWeight: 'bold', padding: '0 8px' }}>Tài xế & Phương tiện</legend>
        <div className="log-form-grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
          <label>
            <strong>Tài xế phụ trách</strong>
            <select required value={driverId} onChange={e => onSelectDriver(e.target.value)}>
              <option value="">{eligible.length ? 'Chọn tài xế' : 'Chưa có tài xế phù hợp'}</option>
              {drivers.map(d => {
                const reasons = driverAssignmentIssues(d, until, vehicleId);
                return (
                  <option key={d.id} value={d.id} disabled={!!reasons.length}>
                    {d.employeeCode || d.id.slice(0, 8)} · {d.displayName}{reasons.length ? ` — ${reasons[0]}` : ''}
                  </option>
                );
              })}
            </select>
          </label>

          <label>
            <strong>Phương tiện</strong>
            <select required value={vehicleId} onChange={e => onSelectVehicle(e.target.value)}>
              <option value="">Chọn phương tiện</option>
              {vehicles.filter(v => v.status === 'available').map(v => (
                <option key={v.id} value={v.id}>
                  {v.plate} · {v.capacityKg} kg
                </option>
              ))}
            </select>
          </label>
        </div>

        {selected && selectedVehicle && !selectedIssues.length && (
          <div style={{ marginTop: '8px', padding: '6px 12px', background: 'rgba(16, 185, 129, 0.1)', borderRadius: '6px', color: '#10b981', fontSize: '13px' }}>
            ✓ Phương tiện: <strong>{selectedVehicle.plate}</strong> ({selectedVehicle.capacityKg} kg) · Tài xế: <strong>{selected.displayName}</strong>
          </div>
        )}

        {isOverweight && (
          <div style={{ marginTop: '8px', padding: '8px 12px', background: 'rgba(239, 68, 68, 0.1)', borderRadius: '6px', color: '#ef4444', fontSize: '13px' }}>
            ⚠️ Tổng hàng ({totalWeight} kg) vượt tải trọng xe ({selectedVehicle?.capacityKg} kg). Vui lòng chọn xe lớn hơn.
          </div>
        )}

        {!!selectedIssues.length && <p className="log-error" style={{ marginTop: '8px' }}>{selected?.displayName}: {selectedIssues.join('; ')}</p>}

        {conflictingVehicleTrip && (
          <div style={{ marginTop: '8px', padding: '8px 12px', background: 'rgba(239, 68, 68, 0.1)', borderRadius: '6px', color: '#ef4444', fontSize: '13px', lineHeight: 1.5 }}>
            ⚠️ Xe <strong>{selectedVehicle?.plate}</strong> đang có lịch chạy chuyến <strong>"{conflictingVehicleTrip.name}"</strong> (từ {new Date(conflictingVehicleTrip.start).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} ngày {new Date(conflictingVehicleTrip.start).toLocaleDateString('vi-VN')}). {conflictingVehicleTrip.status === 'declined' ? 'Chuyến cũ đang ở trạng thái Từ chối, hãy vào Xem chuyến → Hủy chuyến đó để nhả xe.' : 'Hãy chọn xe khác hoặc hủy chuyến cũ trước.'}
          </div>
        )}

        {conflictingDriverTrip && (
          <div style={{ marginTop: '8px', padding: '8px 12px', background: 'rgba(239, 68, 68, 0.1)', borderRadius: '6px', color: '#ef4444', fontSize: '13px', lineHeight: 1.5 }}>
            ⚠️ Tài xế <strong>{selected?.displayName}</strong> đang có lịch chạy chuyến <strong>"{conflictingDriverTrip.name}"</strong>.
          </div>
        )}


        {!!blocked.length && (
          <details className="assignment-blockers" open={!eligible.length} style={{ marginTop: '10px' }}>
            <summary style={{ cursor: 'pointer', fontSize: '12px', color: 'var(--muted)' }}>{blocked.length} tài xế chưa sẵn sàng</summary>
            {blocked.map(d => (
              <div className="assignment-blocker" key={d.id}>
                <div>
                  <strong>{d.displayName} ({d.employeeCode || d.id.slice(0, 8)})</strong>
                  <small>{driverAssignmentIssues(d, until, vehicleId).join(' · ')}</small>
                </div>
                <button type="button" onClick={() => openProfile(d.id)}>
                  {admin ? (d.qualificationStatus === 'approved' && vehicleId && !d.approvedVehicleIds?.includes(vehicleId) ? 'Cấp quyền' : 'Xem hồ sơ') : 'Xem hồ sơ'}
                </button>
              </div>
            ))}
          </details>
        )}
      </fieldset>

      {/* 2. CHỌN ĐƠN HÀNG & GỢI Ý GHÉP ĐƠN CÙNG TUYẾN */}
      <fieldset style={{ border: '1px solid var(--line)', borderRadius: '12px', padding: '14px', marginBottom: '14px' }}>
        <legend style={{ fontWeight: 'bold', padding: '0 8px' }}>
          Đơn hàng vận chuyển {availableOrders.length > 0 && `(${orderIds.length}/${availableOrders.length} đã chọn)`}
        </legend>

        {/* ── BANNER GỢI Ý GHÉP ĐƠN THÔNG MINH NẾU CÙNG TUYẾN ── */}
        {poolGroups.length > 0 && (
          <div style={{ background: 'rgba(37, 99, 235, 0.08)', border: '1px solid rgba(37, 99, 235, 0.25)', borderRadius: '10px', padding: '12px 14px', marginBottom: '14px' }}>
            {poolGroups.map(pg => (
              <div key={pg.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
                <div>
                  <strong style={{ color: '#2563eb', fontSize: '13px' }}>💡 Gợi ý ghép đơn cùng tuyến: {pg.routeName}</strong>
                  <div style={{ fontSize: '12px', color: 'var(--muted)', marginTop: '2px' }}>
                    Có {pg.orders.length} đơn cùng kho xuất phát và hướng giao ({pg.totalWeightKg} kg).
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => applyPool(pg)}
                  style={{
                    padding: '6px 14px',
                    background: '#2563eb',
                    color: '#fff',
                    border: 'none',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    fontWeight: 'bold',
                    fontSize: '12px'
                  }}
                >
                  Ghép {pg.orders.length} đơn này
                </button>
              </div>
            ))}
          </div>
        )}

        {/* ── DANH SÁCH ĐƠN HÀNG SẴN SÀNG (HIỂN THỊ TRỰC TIẾP) ── */}
        {availableOrders.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '14px' }}>
            {availableOrders.map(o => {
              const isSelected = orderIds.includes(o.id);
              const belongsToPool = poolGroups.find(pg => pg.orders.some(x => x.id === o.id));

              return (
                <label 
                  key={o.id}
                  style={{ 
                    border: isSelected ? '2px solid #2563eb' : '1px solid var(--line)', 
                    background: isSelected ? 'rgba(37, 99, 235, 0.06)' : 'var(--surface-variant, rgba(255,255,255,0.02))', 
                    borderRadius: '10px', 
                    padding: '12px 14px', 
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '14px',
                    boxSizing: 'border-box',
                    width: '100%'
                  }}
                >
                  <input 
                    type="checkbox" 
                    checked={isSelected} 
                    onChange={() => toggleOrder(o.id)} 
                    style={{ 
                      width: '18px', 
                      minWidth: '18px', 
                      height: '18px', 
                      flexShrink: 0, 
                      margin: 0, 
                      cursor: 'pointer',
                      accentColor: '#2563eb'
                    }}
                  />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px', gap: '10px' }}>
                      <strong style={{ fontSize: '14px', color: 'var(--text)' }}>
                        {o.customer} <small style={{ color: 'var(--muted)', fontWeight: 'normal', fontSize: '12px' }}>({o.id.slice(0, 8)})</small>
                      </strong>
                      <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexShrink: 0 }}>
                        {belongsToPool && (
                          <span style={{ fontSize: '11px', background: 'rgba(37, 99, 235, 0.15)', color: '#2563eb', padding: '3px 8px', borderRadius: '4px', fontWeight: 600 }}>
                            Cùng tuyến {belongsToPool.routeName}
                          </span>
                        )}
                        <strong style={{ fontSize: '14px', color: '#10b981' }}>{o.weightKg} kg</strong>
                      </div>
                    </div>

                    <div style={{ fontSize: '13px', color: 'var(--muted)', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                      <div style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        <span style={{ color: 'var(--text)', fontWeight: 500 }}>Lấy: </span>
                        {o.pickupAddress}
                      </div>
                      <div style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        <span style={{ color: 'var(--text)', fontWeight: 500 }}>Giao: </span>
                        {o.deliveryAddress}
                      </div>
                    </div>
                  </div>
                </label>
              );
            })}
          </div>
        ) : (
          <p style={{ color: 'var(--muted)', fontSize: '13px', margin: '6px 0 12px 0' }}>
            Không có đơn hàng nào đang chờ xếp chuyến. Bạn có thể thêm điểm giao trực tiếp bên dưới.
          </p>
        )}

        {/* ── TÙY CHỌN THÊM ĐIỂM GIAO TRỰC TIẾP ── */}
        {availableOrders.length > 0 && (
          <button
            type="button"
            onClick={() => {
              setShowManualStops(v => !v);
              if (!directStops.length) addDirectStop();
            }}
            style={{
              background: 'none',
              border: 'none',
              color: '#2563eb',
              cursor: 'pointer',
              fontSize: '13px',
              padding: '4px 0',
              fontWeight: 600
            }}
          >
            {showManualStops ? '− Thu gọn thêm điểm trực tiếp' : '+ Hoặc thêm điểm giao hàng trực tiếp (không qua đơn)'}
          </button>
        )}

        {showManualStops && (
          <div style={{ marginTop: '12px', paddingTop: '12px', borderTop: '1px dashed var(--line)' }}>
            <label style={{ marginBottom: '12px', display: 'block' }}>
              <span style={{ fontSize: '12px', color: 'var(--muted)' }}>Kho lấy hàng xuất phát:</span>
              <input 
                value={pickupHub} 
                onChange={e => setPickupHub(e.target.value)} 
                placeholder="VD: Kho tổng Sóng Thần, Bình Dương" 
              />
            </label>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {directStops.map((stop, idx) => (
                <div key={stop.id} style={{ border: '1px solid var(--line)', borderRadius: '8px', padding: '10px', background: 'var(--surface-variant, rgba(255,255,255,0.03))' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                    <strong style={{ fontSize: '13px' }}>Điểm giao trực tiếp #{idx + 1}</strong>
                    <button type="button" onClick={() => removeDirectStop(idx)} style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', fontSize: '13px' }}>
                      Xóa
                    </button>
                  </div>

                  <div className="log-form-grid" style={{ gridTemplateColumns: '1fr 1fr', marginBottom: '8px' }}>
                    <input 
                      placeholder="Người nhận / Kho hàng" 
                      value={stop.receiver} 
                      onChange={e => updateDirectStop(idx, 'receiver', e.target.value)} 
                    />
                    <input 
                      placeholder="Số điện thoại" 
                      value={stop.receiverPhone} 
                      onChange={e => updateDirectStop(idx, 'receiverPhone', e.target.value)} 
                    />
                  </div>

                  <div style={{ display: 'flex', gap: '8px' }}>
                    <input 
                      style={{ flex: 1 }}
                      placeholder="Địa chỉ giao hoặc link Google Maps" 
                      value={stop.address} 
                      onChange={e => updateDirectStop(idx, 'address', e.target.value)} 
                    />
                    <input 
                      style={{ width: '130px' }}
                      type="number" 
                      placeholder="Khối lượng (kg)" 
                      value={stop.weightKg ? stop.weightKg : ''} 
                      onChange={e => updateDirectStop(idx, 'weightKg', Number(e.target.value))} 
                    />
                  </div>
                </div>
              ))}

              <button 
                type="button" 
                onClick={addDirectStop} 
                disabled={directStops.length >= 10}
                style={{ 
                  padding: '8px', 
                  border: '1.5px dashed var(--line)', 
                  borderRadius: '8px', 
                  background: 'transparent', 
                  color: 'var(--text)', 
                  cursor: 'pointer', 
                  fontWeight: 'bold',
                  fontSize: '12px'
                }}
              >
                + Thêm điểm giao trực tiếp
              </button>
            </div>
          </div>
        )}
      </fieldset>

      {/* 3. THÔNG TIN CHUYẾN & LỊCH TRÌNH */}
      <details style={{ marginBottom: '14px', border: '1px solid var(--line)', borderRadius: '12px', padding: '10px 14px' }}>
        <summary style={{ cursor: 'pointer', fontWeight: 'bold' }}>
          Thông tin chuyến & Lịch trình
        </summary>
        <div style={{ marginTop: '10px' }}>
          <label style={{ display: 'block', marginBottom: '10px' }}>
            <span>Tên chuyến:</span>
            <input 
              value={customName} 
              onChange={e => setCustomName(e.target.value)} 
              placeholder={`Mặc định: ${autoTripName}`} 
            />
          </label>
          <div className="log-form-grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
            <label>
              <span>Khởi hành dự kiến:</span>
              <input type="datetime-local" value={start} onChange={e => setStart(e.target.value)} required />
            </label>
            <label>
              <span>Hoàn thành dự kiến:</span>
              <input type="datetime-local" value={end} onChange={e => setEnd(e.target.value)} required />
            </label>
          </div>
        </div>
      </details>

      {(error || localError) && (
        <p role="alert" className="log-error" style={{ marginBottom: '12px' }}>
          {localError || error}
        </p>
      )}

      <button 
        className="log-submit" 
        disabled={isFormBusy || totalStopsCount === 0 || !driverId || !vehicleId || !!selectedIssues.length || isOverweight || !!conflictingVehicleTrip || !!conflictingDriverTrip}
        style={{ width: '100%', padding: '12px', fontSize: '15px', fontWeight: 'bold' }}
      >
        {isFormBusy ? 'Đang tạo và giao chuyến…' : `Xác nhận giao chuyến (${totalStopsCount} đơn · ${totalWeight} kg)`}
      </button>
    </form>
  );
}
