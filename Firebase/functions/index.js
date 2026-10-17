import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { validateDossier, requireApprovedDossier } from './dossiers.js';

initializeApp();
const db = getFirestore();
const fail = (message, code = 'failed-precondition') => { throw new HttpsError(code, message); };
const text = (value, label, max = 1000) => {
  if (typeof value !== 'string' || !value.trim() || value.length > max) fail(`Thiếu hoặc sai ${label}`, 'invalid-argument');
  return value.trim();
};
const identifier = value => { const v = text(value, 'ID', 128); if (v.includes('/')) fail('ID không hợp lệ', 'invalid-argument'); return v; };
const positive = (v, label) => { if (!Number.isFinite(v) || v <= 0) fail(`${label} phải lớn hơn 0`, 'invalid-argument'); return v; };
const interval = p => { const start = positive(p.start, 'Giờ bắt đầu'), end = positive(p.end, 'Giờ kết thúc'); if (end <= start) fail('Giờ kết thúc phải sau giờ bắt đầu'); return { start, end }; };
const overlaps = (a, b) => a.start < b.end && b.start < a.end;
const ref = (collection, id) => db.collection(collection).doc(identifier(id));
const terminal = status => ['completed', 'completed_issues', 'cancelled'].includes(status);
const clean = schedule => ({ reservations: schedule?.reservations || {}, leaves: schedule?.leaves || {} });
function available(schedule, period, except, now) {
  for (const [id, r] of Object.entries(schedule.reservations)) {
    // An overdue running truck is still occupied. Planned end is not a release signal.
    if (id !== except && (r.running || overlaps(r, period))) fail('Tài xế hoặc xe đã có chuyến trùng lịch / đang chạy');
  }
  for (const leave of Object.values(schedule.leaves)) if (overlaps(leave, period) || (period.running && leave.start <= now && now < leave.end)) fail('Tài xế đang nghỉ đã được duyệt');
}

/** All business mutations pass here. Clients cannot write dispatch state directly.
 * Calendar documents serialize competing assignments without trusting dropdown filters.
 * operationId provides retry safety when a mobile response is lost after commit.
 */
export const dispatchCommand = onCall({ region: 'asia-southeast1', maxInstances: 10 }, async request => {
  const uid = request.auth?.uid;
  if (!uid) fail('Cần đăng nhập', 'unauthenticated');
  const p = request.data || {}, action = text(p.action, 'thao tác', 50);
  const operationId = identifier(p.operationId), now = Date.now();
  const receiptRef = ref('dispatchReceipts', `${uid}_${operationId}`);
  return db.runTransaction(async tx => {
    const [staffSnap, driverSnap, receipt] = await tx.getAll(ref('staff', uid), ref('drivers', uid), receiptRef);
    if (receipt.exists) return receipt.data().result;
    const staff = staffSnap.data(), driver = driverSnap.data();
    const isStaff = staff?.active === true && ['admin', 'dispatcher'].includes(staff.role);
    const admin = () => { if (!isStaff || staff.role !== 'admin') fail('Cần quyền quản trị', 'permission-denied'); };
    const dispatcher = () => { if (!isStaff) fail('Cần quyền điều phối', 'permission-denied'); };
    const activeDriver = () => { if (driver?.accountStatus !== 'active') fail('Tài khoản tài xế chưa hoạt động', 'permission-denied'); };
    const result = { id: operationId };
    const auditRef = db.collection('dispatchAudit').doc();
    const saveReceipt = () => {
      tx.create(receiptRef, { uid, action, at: now, result });
      tx.create(auditRef, { actorId: uid, action, targetId: p.tripId || p.leaveId || p.driverId || operationId, at: now,
        reason: action === 'reviewDriverDossier' ? 'Ghi chú đối chiếu nằm trong hồ sơ riêng tư' : String(p.reason || '').slice(0, 1000) });
      return result;
    };

    if (action === 'submitDriverDossier') {
      if (!driverSnap.exists || driver?.accountStatus === 'locked') fail('Cần hồ sơ tài khoản tài xế đang được phép cập nhật', 'permission-denied');
      const dossierRef = ref('driverDossiers', uid), previous = (await tx.get(dossierRef)).data();
      if ((previous?.revision || 0) !== p.revision) fail('Hồ sơ đã thay đổi trên thiết bị khác. Tải lại trước khi nộp.');
      const fields = await validateDossier(p, uid, now);
      const submitted = { ...fields, driverId: uid, revision: (previous?.revision || 0) + 1,
        status: 'submitted', submittedAt: now, reviewedBy: null, reviewedAt: null, reviewNote: '' };
      tx.set(dossierRef, submitted);
      tx.create(dossierRef.collection('revisions').doc(String(submitted.revision)), submitted);
      tx.update(driverSnap.ref, { displayName: fields.fullName, phone: fields.phone, qualificationStatus: 'submitted', updatedAt: now });
      return saveReceipt();
    }
    if (action === 'reviewDriverDossier') {
      admin();
      const dossierRef = ref('driverDossiers', p.driverId), dossier = (await tx.get(dossierRef)).data();
      const target = await tx.get(ref('drivers', p.driverId));
      if (!target.exists || !dossier || dossier.status !== 'submitted' || dossier.revision !== p.revision) fail('Hồ sơ đã đổi hoặc không còn chờ duyệt. Mở lại hồ sơ.');
      if (!['approved', 'rejected'].includes(p.status)) fail('Kết quả duyệt không hợp lệ');
      let approvedVehicleIds = [];
      if (p.status === 'approved') {
        if (p.verified !== true) fail('Cần xác nhận đã đối chiếu giấy tờ và điều kiện loại xe');
        if (dossier.licenseExpiresAt <= now || dossier.healthExpiresAt <= now) fail('Giấy tờ đã hết hạn');
        if (!Array.isArray(p.vehicleIds) || !p.vehicleIds.length || p.vehicleIds.length > 100) fail('Chọn các xe quản lý đã xác nhận tài xế được điều khiển');
        approvedVehicleIds = [...new Set(p.vehicleIds.map(identifier))];
        const vehicles = await tx.getAll(...approvedVehicleIds.map(id => ref('fleetVehicles', id)));
        if (vehicles.some(v => !v.exists)) fail('Có xe không còn trong danh sách công ty');
      }
      const note = text(p.reason, p.status === 'rejected' ? 'lý do yêu cầu bổ sung' : 'ghi chú đối chiếu');
      tx.update(dossierRef, { status: p.status, reviewNote: note, reviewedBy: uid, reviewedAt: now, approvedVehicleIds });
      tx.set(dossierRef.collection('revisions').doc(String(dossier.revision)), { ...dossier,
        status: p.status, reviewNote: note, reviewedBy: uid, reviewedAt: now, approvedVehicleIds });
      tx.update(target.ref, { qualificationStatus: p.status, licenseExpiresAt: dossier.licenseExpiresAt,
        healthExpiresAt: dossier.healthExpiresAt, licenseClass: dossier.licenseClass, dossierRevision: dossier.revision, approvedVehicleIds, updatedAt: now });
      return saveReceipt();
    }
    if (action === 'updateMyStaffProfile') {
      dispatcher();
      tx.update(staffSnap.ref, { displayName: text(p.displayName, 'họ tên', 120), phone: text(p.phone, 'điện thoại', 30), updatedAt: now });
      return saveReceipt();
    }

    if (action === 'createOrder') {
      dispatcher();
      const items = p.items;
      if (!Array.isArray(items) || !items.length || items.length > 30) fail('Cần từ 1 đến 30 mặt hàng');
      const order = {
        customer: text(p.customer, 'khách hàng', 200), customerPhone: text(p.customerPhone, 'SĐT khách', 40),
        sender: text(p.sender, 'liên hệ lấy hàng', 200), senderPhone: text(p.senderPhone, 'SĐT liên hệ lấy hàng', 40),
        pickupAddress: text(p.pickupAddress, 'địa chỉ nhận'), receiver: text(p.receiver, 'người nhận', 200),
        receiverPhone: text(p.receiverPhone, 'SĐT người nhận', 40), deliveryAddress: text(p.deliveryAddress, 'địa chỉ giao'),
        items: items.map(i => ({ name: text(i.name, 'tên hàng', 200), quantity: positive(i.quantity, 'Số lượng'), unit: text(i.unit, 'đơn vị', 30) })),
        weightKg: positive(p.weightKg, 'Khối lượng'), ...interval(p), note: String(p.note || '').slice(0, 1000),
        status: 'ready', tripId: null, createdBy: uid, createdAt: now, updatedAt: now,
      };
      tx.create(ref('transportOrders', operationId), order);
      return saveReceipt();
    }
    if (action === 'saveStaff') {
      admin();
      const targetId = identifier(p.staffId);
      if (targetId === uid && (p.role !== 'admin' || p.active !== true)) fail('Không tự thu hồi quyền quản trị của chính mình');
      if (!['admin', 'dispatcher'].includes(p.role) || typeof p.active !== 'boolean') fail('Sai vai trò hoặc trạng thái');
      // Grant only an existing Firebase Auth identity; never invent a usable login from a profile document.
      const account = await getAuth().getUser(targetId).catch(() => fail('Không tìm thấy UID tài khoản Firebase Auth'));
      tx.set(ref('staff', targetId), { role: p.role, active: p.active, displayName: text(p.displayName, 'tên nhân viên', 200),
        employeeCode: text(p.employeeCode, 'mã nhân viên', 40), email: account.email || '', phone: String(p.phone || '').slice(0, 30), updatedBy: uid, updatedAt: now });
      return saveReceipt();
    }
    if (action === 'reviewAlert') {
      dispatcher();
      if (!['NEW', 'ACKNOWLEDGED', 'RESOLVED'].includes(p.status)) fail('Trạng thái xử lý không hợp lệ');
      const alertRef = ref('alerts', p.eventId), alert = await tx.get(alertRef);
      if (!alert.exists) fail('Không tìm thấy cảnh báo');
      tx.update(alertRef, { status: p.status, note: String(p.reason || '').slice(0, 1000), handledBy: uid, handledAt: now });
      return saveReceipt();
    }
    if (action === 'saveVehicle') {
      admin();
      const vehicleRef = ref('fleetVehicles', p.vehicleId || operationId), old = await tx.get(vehicleRef);
      const plate = text(p.plate, 'biển số', 30).toUpperCase(), plateKey = plate.replace(/[^A-Z0-9]/g, '');
      if (!plateKey) fail('Biển số không hợp lệ');
      const plateRef = ref('fleetVehiclePlates', plateKey), plateOwner = await tx.get(plateRef);
      if (plateOwner.exists && plateOwner.data().vehicleId !== vehicleRef.id) fail('Biển số đã thuộc một xe khác');
      const calendar = await tx.get(ref('resourceCalendars', `vehicle_${vehicleRef.id}`));
      const schedule = clean(calendar.data());
      if (Object.keys(schedule.reservations).length) fail('Xe còn chuyến đã phân công; kết thúc/hủy chuyến trước khi sửa hồ sơ xe');
      const previousKey = old.data()?.plate?.replace(/[^A-Z0-9]/g, '');
      if (previousKey && previousKey !== plateKey) tx.delete(ref('fleetVehiclePlates', previousKey));
      tx.set(plateRef, { vehicleId: vehicleRef.id });
      tx.set(vehicleRef, { plate, capacityKg: positive(p.capacityKg, 'Tải trọng'),
        status: ['available', 'maintenance', 'inactive'].includes(p.status) ? p.status : 'available',
        updatedBy: uid, updatedAt: now, createdAt: old.data()?.createdAt || now });
      result.id = vehicleRef.id;
      return saveReceipt();
    }
    if (action === 'driverStatus') {
      admin();
      const driverRef = ref('drivers', p.driverId), d = await tx.get(driverRef);
      if (!d.exists) fail('Tài xế phải đăng ký tài khoản từ app trước');
      if (!['active', 'locked', 'rejected'].includes(p.status)) fail('Trạng thái không hợp lệ');
      if (p.status === 'active') {
        requireApprovedDossier(d.data(), now);
        const account = await getAuth().getUser(p.driverId);
        if (!account.emailVerified) fail('Tài xế chưa xác minh email');
      }
      const schedule = clean((await tx.get(ref('resourceCalendars', `driver_${p.driverId}`))).data());
      if (p.status !== 'active' && Object.values(schedule.reservations).some(r => r.running)) fail('Cần bàn giao hoặc kết thúc chuyến đang chạy trước khi khóa');
      tx.update(driverRef, { accountStatus: p.status, employeeCode: text(p.employeeCode, 'mã nhân viên', 40), updatedAt: now });
      return saveReceipt();
    }
    if (action === 'requestLeave') {
      activeDriver();
      tx.create(ref('leaveRequests', operationId), { driverId: uid, ...interval(p), reason: text(p.reason, 'lý do nghỉ'), status: 'pending', createdAt: now });
      return saveReceipt();
    }
    if (action === 'reviewLeave') {
      admin();
      const leaveRef = ref('leaveRequests', p.leaveId), leave = (await tx.get(leaveRef)).data();
      if (!leave || leave.status !== 'pending') fail('Đơn nghỉ đã xử lý hoặc không tồn tại');
      if (!['approved', 'rejected'].includes(p.status)) fail('Kết quả duyệt không hợp lệ');
      const calRef = ref('resourceCalendars', `driver_${leave.driverId}`), calendar = clean((await tx.get(calRef)).data());
      const affected = Object.entries(calendar.reservations).filter(([, r]) => overlaps(r, leave) || (r.running && leave.start <= now && now < leave.end));
      if (p.status === 'approved' && affected.some(([, r]) => r.running)) fail('Có chuyến đang chạy trong thời gian nghỉ; cần bàn giao trước');
      // Read every affected trip before any write (Firestore transaction requirement).
      const trips = p.status === 'approved' ? await Promise.all(affected.map(([id]) => tx.get(ref('dispatchTrips', id)))) : [];
      if (p.status === 'approved') {
        calendar.leaves[p.leaveId] = { start: leave.start, end: leave.end };
        for (const trip of trips) {
          if (trip.exists && !terminal(trip.data().status)) tx.update(trip.ref, { status: 'needs_reassignment', updatedAt: now });
          delete calendar.reservations[trip.id];
        }
        // Keep only leave intervals that may still affect a future start.
        calendar.leaves = Object.fromEntries(Object.entries(calendar.leaves).filter(([, l]) => l.end > now));
        tx.set(calRef, calendar);
      }
      tx.update(leaveRef, { status: p.status, reviewedBy: uid, reviewedAt: now, reviewNote: String(p.reason || '').slice(0, 1000), affectedTripIds: trips.map(t => t.id) });
      return saveReceipt();
    }

    const create = action === 'assignTrip';
    const tripRef = ref('dispatchTrips', create ? operationId : p.tripId);
    const old = (await tx.get(tripRef)).data();
    if (!create && !old) fail('Không tìm thấy chuyến');
    const assignment = ['assignTrip', 'reassignTrip', 'handover'].includes(action);
    const driverId = assignment ? identifier(p.driverId) : old.driverId;
    const vehicleId = create ? identifier(p.vehicleId) : old.vehicleId;
    const [driverDoc, vehicleDoc, driverCalendarDoc, vehicleCalendarDoc] = await tx.getAll(
      ref('drivers', driverId), ref('fleetVehicles', vehicleId), ref('resourceCalendars', `driver_${driverId}`), ref('resourceCalendars', `vehicle_${vehicleId}`));
    const driverCalendar = clean(driverCalendarDoc.data()), vehicleCalendar = clean(vehicleCalendarDoc.data());
    const previousCalendarDoc = assignment && old && old.driverId !== driverId ? await tx.get(ref('resourceCalendars', `driver_${old.driverId}`)) : null;
    const orders = create ? [...new Set(p.orderIds || [])] : old.orderIds;
    if (!Array.isArray(orders) || !orders.length || orders.length > 20) fail('Chuyến cần từ 1 đến 20 đơn');
    const orderDocs = await Promise.all(orders.map(id => tx.get(ref('transportOrders', id))));
    const trip = create ? {
      name: text(p.name, 'tên chuyến', 200), ...interval(p), driverId, vehicleId, orderIds: orders,
      createdBy: uid, createdAt: now, dispatcherId: uid, assignmentVersion: 0, history: [], stops: [],
    } : { ...old };
    if (create && trip.end <= now) fail('Thời gian kết thúc kế hoạch phải còn ở tương lai');
    if (!create && !isStaff) {
      activeDriver();
      if (old.driverId !== uid || old.assignmentVersion !== p.assignmentVersion) fail('Phân công đã thay đổi. Tải lại chuyến trước khi thao tác.', 'permission-denied');
    }
    if (assignment) {
      dispatcher();
      if (!create && (action === 'handover' ? !['in_progress', 'handover_pending'].includes(old.status) : !['assigned', 'accepted', 'needs_reassignment', 'declined'].includes(old.status))) fail('Không thể đổi phân công ở trạng thái hiện tại');
      if (!create && old.driverId === driverId) fail('Hãy chọn tài xế thay thế khác');
      if (driverDoc.data()?.accountStatus !== 'active') fail('Tài xế chưa được duyệt hoặc đang khóa');
      requireApprovedDossier(driverDoc.data(), Math.max(trip.end, now), vehicleId);
      if (vehicleDoc.data()?.status !== 'available') fail('Xe không sẵn sàng');
      const weight = orderDocs.reduce((sum, d) => sum + (d.data()?.weightKg || 0), 0);
      if (weight > vehicleDoc.data().capacityKg) fail('Tổng hàng vượt tải trọng xe');
      const period = { start: action === 'handover' ? now : trip.start, end: Math.max(trip.end, now + 1), running: action === 'handover' };
      available(driverCalendar, period, tripRef.id, now); available(vehicleCalendar, period, tripRef.id, now);
      if (create) {
        for (const d of orderDocs) if (!d.exists || d.data().status !== 'ready' || d.data().tripId) fail('Một đơn đã thuộc chuyến khác / chưa sẵn sàng');
        // Explicit order defines pickup/delivery sequencing; never infer cargo delivery from GPS.
        const stopKeys = p.stopKeys || orders.flatMap(id => [`${id}:pickup`, `${id}:delivery`]);
        if (!Array.isArray(stopKeys) || stopKeys.length !== orders.length * 2 || new Set(stopKeys).size !== stopKeys.length) fail('Sai danh sách điểm dừng');
        for (const id of orders) if (!stopKeys.includes(`${id}:pickup`) || !stopKeys.includes(`${id}:delivery`) || stopKeys.indexOf(`${id}:pickup`) > stopKeys.indexOf(`${id}:delivery`)) fail('Mỗi đơn cần nhận hàng trước khi giao');
        trip.stops = stopKeys.map(key => {
          const [orderId, kind] = key.split(':'), o = orderDocs.find(d => d.id === orderId).data();
          return { id: key, orderId, kind, address: kind === 'pickup' ? o.pickupAddress : o.deliveryAddress,
            contact: kind === 'pickup' ? o.sender : o.receiver, phone: kind === 'pickup' ? o.senderPhone : o.receiverPhone,
            items: o.items, status: 'pending' };
        });
      }
      const reason = create ? 'Phân công ban đầu' : text(p.reason, 'lý do đổi tài xế');
      const history = [...trip.history, { from: old?.driverId || null, to: driverId, by: uid, at: now, reason,
        location: String(p.location || '').slice(0, 500), cargoNote: String(p.cargoNote || '').slice(0, 1000), version: trip.assignmentVersion + 1 }];
      if (history.length > 100) fail('Đã vượt giới hạn 100 lần bàn giao/chuyến');
      if (action === 'handover') { text(p.location, 'vị trí bàn giao'); text(p.cargoNote, 'tình trạng hàng'); }
      Object.assign(trip, { driverId, driverName: driverDoc.data().fullName || driverDoc.data().displayName || driverId,
        vehiclePlate: vehicleDoc.data().plate, dispatcherId: uid, assignmentVersion: trip.assignmentVersion + 1,
        status: action === 'handover' ? 'handover_pending' : 'assigned', history, assignedAt: now });
      if (previousCalendarDoc) {
        const previous = clean(previousCalendarDoc.data()); delete previous.reservations[tripRef.id]; tx.set(previousCalendarDoc.ref, previous);
      }
      driverCalendar.reservations[tripRef.id] = period; vehicleCalendar.reservations[tripRef.id] = period;
      tx.create(ref('tripAssignments', `${tripRef.id}_${driverId}_${trip.assignmentVersion}`),
        { tripId: tripRef.id, driverId, assignmentVersion: trip.assignmentVersion, assignedAt: now, assignedBy: uid });
      if (create) for (const d of orderDocs) tx.update(d.ref, { tripId: tripRef.id, status: 'assigned', updatedAt: now });
    } else if (action === 'acceptTrip') {
      if (isStaff) fail('Tài xế phải tự nhận chuyến');
      if (!['assigned', 'handover_pending'].includes(trip.status)) fail('Chuyến không chờ nhận');
      requireApprovedDossier(driverDoc.data(), Math.max(trip.end, now), vehicleId);
      const running = trip.status === 'handover_pending';
      trip.status = running ? 'in_progress' : 'accepted'; trip.acceptedAt = now;
    } else if (action === 'declineTrip') {
      if (isStaff || trip.status !== 'assigned') fail('Chỉ tài xế được từ chối chuyến chưa nhận');
      trip.status = 'declined'; trip.declineReason = text(p.reason, 'lý do từ chối');
      delete driverCalendar.reservations[tripRef.id];
      // Vehicle and orders remain reserved until dispatcher reassigns or cancels.
    } else if (action === 'startTrip') {
      if (isStaff || trip.status !== 'accepted') fail('Tài xế cần nhận chuyến trước khi bắt đầu');
      if (vehicleDoc.data()?.status !== 'available') fail('Xe không sẵn sàng');
      requireApprovedDossier(driverDoc.data(), Math.max(trip.end, now), vehicleId);
      const period = { start: Math.min(now, trip.start), end: Math.max(now + 1, trip.end), running: true };
      available(driverCalendar, period, tripRef.id, now); available(vehicleCalendar, period, tripRef.id, now);
      driverCalendar.reservations[tripRef.id] = period; vehicleCalendar.reservations[tripRef.id] = period;
      trip.status = 'in_progress'; trip.actualStart = now;
    } else if (action === 'updateStop') {
      if (isStaff || trip.status !== 'in_progress') fail('Tài xế chỉ cập nhật điểm khi chuyến đang chạy');
      const index = trip.stops.findIndex(s => s.status === 'pending');
      if (index < 0 || trip.stops[index].id !== p.stopId) fail('Cần xử lý điểm dừng theo thứ tự');
      const stop = trip.stops[index];
      if (!['full', 'partial', 'failed'].includes(p.outcome)) fail('Kết quả không hợp lệ');
      const quantities = p.quantities;
      if (!Array.isArray(quantities) || quantities.length !== stop.items.length || quantities.some((q, i) => !Number.isFinite(q) || q < 0 || q > stop.items[i].quantity)) fail('Sai số lượng thực nhận/giao');
      const full = quantities.every((q, i) => q === stop.items[i].quantity), zero = quantities.every(q => q === 0);
      if ((p.outcome === 'full' && !full) || (p.outcome === 'failed' && !zero) || (p.outcome === 'partial' && (full || zero))) fail('Kết quả không khớp số lượng');
      // Delivery cannot exceed the quantity actually collected at pickup.
      if (stop.kind === 'delivery') {
        const pickup = trip.stops.find(s => s.orderId === stop.orderId && s.kind === 'pickup');
        if (quantities.some((q, i) => q > (pickup.quantities?.[i] || 0))) fail('Không thể giao nhiều hơn số lượng đã nhận');
      }
      const note = p.outcome === 'full' ? String(p.reason || '').slice(0, 1000) : text(p.reason, 'lý do thiếu/thất bại');
      trip.stops = trip.stops.map((s, i) => i === index ? { ...s, status: p.outcome, quantities, note, actualContact: text(p.actualContact, 'người giao/nhận thực tế', 200), completedAt: now, driverId: uid, assignmentVersion: trip.assignmentVersion } : s);
      if (stop.kind === 'delivery') tx.update(ref('transportOrders', stop.orderId), { status: p.outcome === 'full' ? 'delivered' : 'delivery_issue', updatedAt: now, deliveryResult: trip.stops[index] });
    } else if (action === 'finishTrip') {
      if (isStaff || trip.status !== 'in_progress') fail('Chỉ tài xế kết thúc chuyến đang chạy');
      if (trip.stops.some(s => s.status === 'pending')) fail('Cần ghi kết quả của tất cả điểm dừng trước khi kết thúc');
      trip.status = trip.stops.every(s => s.status === 'full') ? 'completed' : 'completed_issues';
      if (trip.status === 'completed_issues') trip.issueReason = text(p.reason, 'ghi chú kết thúc có vấn đề');
      trip.actualEnd = now;
      delete driverCalendar.reservations[tripRef.id]; delete vehicleCalendar.reservations[tripRef.id];
    } else if (action === 'cancelTrip') {
      dispatcher();
      if (!['assigned', 'accepted', 'declined', 'needs_reassignment'].includes(trip.status)) fail('Chỉ hủy chuyến trước khi chạy');
      trip.status = 'cancelled'; trip.cancelReason = text(p.reason, 'lý do hủy'); trip.actualEnd = now;
      delete driverCalendar.reservations[tripRef.id]; delete vehicleCalendar.reservations[tripRef.id];
      for (const d of orderDocs) tx.update(d.ref, { tripId: null, status: 'ready', updatedAt: now });
    } else if (action === 'resolveIssue') {
      dispatcher();
      if (trip.status !== 'completed_issues') fail('Chuyến không có vấn đề cần xử lý');
      trip.resolution = { by: uid, at: now, note: text(p.reason, 'kết quả xử lý') };
    } else fail('Thao tác chưa hỗ trợ', 'invalid-argument');

    trip.updatedAt = now;
    tx.set(tripRef, trip);
    tx.set(driverCalendarDoc.ref, driverCalendar); tx.set(vehicleCalendarDoc.ref, vehicleCalendar);
    result.id = tripRef.id;
    return saveReceipt();
  });
});

/** Admin directory reads Auth identities, not email suffix guesses. Paginate to keep
 * personal contact data bounded; no passwords or identity documents are returned. */
export const listCompanyAccounts = onCall({ region: 'asia-southeast1', maxInstances: 5 }, async request => {
  if (!request.auth) fail('Cần đăng nhập', 'unauthenticated');
  const staff = (await ref('staff', request.auth.uid).get()).data();
  if (!staff?.active || staff.role !== 'admin') fail('Chỉ quản trị được xem danh sách tài khoản', 'permission-denied');
  const pageToken = request.data?.pageToken;
  if (pageToken != null && (typeof pageToken !== 'string' || pageToken.length > 2000)) fail('Sai trang tài khoản', 'invalid-argument');
  const page = await getAuth().listUsers(50, pageToken || undefined);
  if (!page.users.length) return { accounts: [], nextPageToken: null };
  const documents = await db.getAll(...page.users.flatMap(user => [ref('staff', user.uid), ref('drivers', user.uid)]));
  return { accounts: page.users.map((user, i) => {
    const employee = documents[i * 2].data(), driver = documents[i * 2 + 1].data();
    return { uid: user.uid, email: user.email || '', displayName: employee?.displayName || driver?.displayName || user.displayName || '',
      emailVerified: user.emailVerified, disabled: user.disabled, providers: user.providerData.map(p => p.providerId),
      staffRole: employee?.role || null, staffActive: employee?.active === true, driverStatus: driver?.accountStatus || null,
      createdAt: user.metadata.creationTime, lastSignInAt: user.metadata.lastSignInTime || null };
  }), nextPageToken: page.pageToken || null };
});
