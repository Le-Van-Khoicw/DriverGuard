import { beforeEach, expect, it, vi } from 'vitest';
const sdk = vi.hoisted(() => ({ getDocs: vi.fn(), getDoc: vi.fn(), setDoc: vi.fn(), updateDoc: vi.fn(), onSnapshot: vi.fn(), signIn: vi.fn(), signOut: vi.fn() }));
vi.mock('./firebase', () => ({ db: {}, auth: {} }));
vi.mock('firebase/auth', () => ({ signInWithEmailAndPassword: sdk.signIn, signOut: sdk.signOut }));
vi.mock('firebase/firestore', () => ({ ...sdk,
  collection: (_db: unknown, name: string) => name, doc: (_db: unknown, name: string, id: string) => `${name}/${id}`,
  query: (ref: string) => ref, orderBy: vi.fn(), limit: vi.fn(), where: vi.fn(), deleteDoc: vi.fn(),
  Timestamp: class { toDate() { return new Date('2026-10-16T00:00:00Z'); } },
}));
import { api } from './client';
import { Timestamp } from 'firebase/firestore';
type Data = Record<string, unknown>;
let records: Record<string, Data[]>;
beforeEach(() => {
  vi.resetAllMocks();
  records = {
    drivers: [{ id: 'driver-1', displayName: 'Nguyễn An', email: 'an@example.com', phone: '0901234567', accountStatus: 'active', vehicleName: 'Xe An', licensePlate: '51A', deviceName: 'Camera An', deviceCode: 'CAM', createdAt: 1000, updatedAt: 2000 }],
    alerts: [{ id: 'alert-1', userId: 'driver-1', timestamp: Date.now(), ear: 0.18, closedDurationSec: 3, imageUrl: 'https://example.com/evidence.jpg', latitude: 0, longitude: 0 }],
    trips: [{ id: 'trip-1', userId: 'driver-1', startTime: Date.now() }],
  };
  sdk.getDocs.mockImplementation(async (name: string) => ({ docs: (records[name] ?? []).map((data, index) => ({ id: String(data.id ?? `id-${index}`), data: () => data })) }));
  sdk.getDoc.mockImplementation(async () => ({ data: () => records.drivers[0] }));
  sdk.signOut.mockResolvedValue(undefined); sdk.updateDoc.mockResolvedValue(undefined); sdk.setDoc.mockResolvedValue(undefined);
});
it.each(['admin', 'admin@driverguard.vn', 'admin@gmail.com'])('keeps explicit demo login %s', async username => {
  await api.login(username, 'admin123'); expect(api.hasToken()).toBe(true); expect(sdk.signIn).not.toHaveBeenCalled();
});
it.each([['employee', 'employee@driverguard.vn'], ['an@example.com', 'an@example.com']])('authenticates %s with Firebase', async (username, email) => {
  sdk.signIn.mockResolvedValue({ user: { getIdToken: async () => 'firebase-token' } });
  await api.login(username, 'password'); expect(sdk.signIn).toHaveBeenCalledWith(expect.anything(), email, 'password');
  expect(sessionStorage.getItem('driverguard_token')).toBe('firebase-token');
  api.logout(); expect(api.hasToken()).toBe(false); expect(sdk.signOut).toHaveBeenCalledOnce();
});
it('handles rejected Firebase auth according to existing demo behavior', async () => {
  sdk.signIn.mockRejectedValue(new Error('offline'));
  await expect(api.login('admin', 'wrong')).rejects.toThrow('6 ký tự'); expect(api.hasToken()).toBe(false);
  await api.login('employee', 'password'); expect(sessionStorage.getItem('driverguard_token')).toBe('demo_employee');
  sdk.signOut.mockRejectedValue(new Error('offline')); api.logout(); await Promise.resolve(); expect(api.hasToken()).toBe(false);
});
it('reads rejected employees and preserves reason', async () => {
  records.drivers[0] = { ...records.drivers[0], accountStatus: 'rejected', rejectReason: 'Thiếu hồ sơ' };
  expect((await api.users())[0]).toMatchObject({ id: 'driver-1', accountStatus: 'rejected', rejectReason: 'Thiếu hồ sơ', isActive: false });
  expect(sdk.getDocs).toHaveBeenCalledWith('drivers');
});
it('normalizes admins, legacy accounts and missing profile fields', async () => {
  records.drivers = [{ role: 'admin' }, { is_active: false }, { accountStatus: 'pending' }];
  const users = await api.users(); expect(users[0]).toMatchObject({ role: 'admin', phone: null, username: null, isActive: true });
  expect(users[1].accountStatus).toBe('locked'); expect(users[2].isActive).toBe(false);
});
it('persists approval transitions and clears old rejection reasons', async () => {
  await api.updateUser('driver-1', { accountStatus: 'rejected', rejectReason: ' Thiếu hồ sơ ' });
  expect(sdk.updateDoc).toHaveBeenLastCalledWith('drivers/driver-1', expect.objectContaining({ rejectReason: 'Thiếu hồ sơ', isActive: false, is_active: false }));
  await api.updateUser('driver-1', { accountStatus: 'active' });
  expect(sdk.updateDoc).toHaveBeenLastCalledWith('drivers/driver-1', expect.objectContaining({ rejectReason: '', isActive: true }));
  records.drivers = []; expect(await api.updateUser('driver-1', { phone: '123' })).toMatchObject({ phone: null });
});
it('rejects invalid rejection before writing and propagates permissions', async () => {
  await expect(api.updateUser('driver-1', { accountStatus: 'rejected' })).rejects.toThrow('lý do'); expect(sdk.updateDoc).not.toHaveBeenCalled();
  sdk.updateDoc.mockRejectedValue(new Error('permission-denied'));
  await expect(api.updateUser('driver-1', { accountStatus: 'active' })).rejects.toThrow('permission-denied');
  sdk.getDocs.mockRejectedValue(new Error('permission-denied')); await expect(api.users()).rejects.toThrow('permission-denied');
  sdk.getDocs.mockRejectedValue('offline'); await expect(api.users()).rejects.toThrow('Không thể tải');
});
it('retains legacy profile-only creation, not an Auth account', async () => {
  const user = await api.createUser({ full_name: 'An', phone: '123' });
  expect(sdk.setDoc).toHaveBeenCalledWith(`drivers/${user.id}`, expect.objectContaining({ displayName: 'An', phone: '123' }));
  await api.createUser({ full_name: 'Bình' });
});
it('subscribes and disposes all three listeners', () => {
  const stops = [vi.fn(), vi.fn(), vi.fn()]; stops.forEach(stop => sdk.onSnapshot.mockReturnValueOnce(stop));
  const updated = vi.fn(); const unsubscribe = api.subscribeRealtime(updated);
  expect(sdk.onSnapshot.mock.calls.map(call => call[0])).toEqual(['drivers', 'alerts', 'trips']);
  sdk.onSnapshot.mock.calls[0][1](); expect(updated).toHaveBeenCalledOnce();
  sdk.onSnapshot.mock.calls.forEach(call => call[2](new Error('offline')));
  unsubscribe(); stops.forEach(stop => expect(stop).toHaveBeenCalledOnce());
});
it('maps mobile devices, vehicles, bindings and sessions', async () => {
  expect((await api.devices())[0]).toMatchObject({ id: 'dev_driver-1', deviceName: 'Camera An' });
  expect((await api.vehicles())[0]).toMatchObject({ id: 'veh_driver-1', licensePlate: '51A' });
  expect((await api.bindings())[0].deviceId).toBe('dev_driver-1');
  expect((await api.sessions())[0]).toMatchObject({ status: 'active', endedAt: null });
  records.trips.push({ startTime: '2026-10-16', endTime: 2000 }); expect((await api.sessions())[1]).toMatchObject({ userId: '', status: 'ended' });
});
it('handles sparse devices and filters drivers without vehicles', async () => {
  records.drivers = [{}, { licensePlate: '51B' }, { vehicleName: 'Xe' }];
  expect((await api.devices())[0].deviceName).toBe('Điện thoại tài xế'); expect(await api.vehicles()).toHaveLength(2);
});
it('keeps evidence images, zero GPS and duration units', async () => {
  const result = await api.events(); expect(result.items[0]).toMatchObject({ imageUrl: 'https://example.com/evidence.jpg', latitude: 0, longitude: 0, closedDurationMs: 3000 });
  expect(result.total).toBe(1); expect((await api.latestLocations())[0]).toMatchObject({ latitude: 0, speedKmh: null });
  expect(await api.locations('driver-1')).toHaveLength(1); expect(await api.locations('other')).toEqual([]);
});
it('maps sparse events and deduplicates locations by employee', async () => {
  records.alerts = [{}, { userId: 'driver-1', latitude: 10, longitude: 106, speedKmh: 40 }, { userId: 'driver-1', latitude: 11, longitude: 107 }];
  expect((await api.events()).items[0]).toMatchObject({ imageUrl: null, note: null, latitude: null, status: 'NEW' });
  expect(await api.latestLocations()).toHaveLength(1); expect(await api.locations()).toHaveLength(2);
  expect((await api.recentAlerts())[0]).toMatchObject({ sessionId: 'session', status: 'NEW' });
});
it('normalizes Firestore timestamps and existing ISO strings', async () => {
  records.drivers = [{ createdAt: new Timestamp(0, 0), updatedAt: '2026-10-16' }, { createdAt: {}, updatedAt: null }];
  const users = await api.users(); expect(users[0].createdAt).toBe('2026-10-16T00:00:00.000Z');
  expect(users[0].updatedAt).toBe('2026-10-16'); expect(Date.parse(users[1].createdAt)).not.toBeNaN();
});
it('reads and writes handling status and note', async () => {
  records.alerts = [{ status: 'RESOLVED', note: 'Đã xử lý', handledBy: 'admin', timestamp: '2026-10-16' }];
  expect((await api.events()).items[0]).toMatchObject({ status: 'RESOLVED', handledBy: 'admin', note: 'Đã xử lý' });
  expect((await api.recentAlerts(1))[0].status).toBe('RESOLVED');
  await api.updateEventStatus('alert-1', 'RESOLVED', 'Đã gọi');
  expect(sdk.updateDoc).toHaveBeenLastCalledWith('alerts/alert-1', { status: 'RESOLVED', note: 'Đã gọi' });
  await api.updateEventStatus('alert-1', 'NEW'); expect(sdk.updateDoc).toHaveBeenLastCalledWith('alerts/alert-1', { status: 'NEW', note: null });
});
it.each(['devices', 'vehicles', 'bindings', 'sessions', 'locations', 'latestLocations', 'recentAlerts', 'alertTrend'] as const)('retains empty fallback on %s failure', async method => {
  sdk.getDocs.mockRejectedValue(new Error('offline')); expect(await api[method]()).toEqual([]);
});
it('returns empty event page on read error', async () => {
  sdk.getDocs.mockRejectedValue(new Error('offline')); expect((await api.events()).items).toEqual([]);
});
it('aggregates today dashboard and seven day trends', async () => {
  expect(await api.dashboard()).toMatchObject({ totalDevices: 1, alertsToday: 1, sessionsToday: 1, unhandledAlerts: 1 });
  const trend = await api.alertTrend(7); expect(trend).toHaveLength(7); expect(trend.reduce((sum, row) => sum + row.count, 0)).toBe(1);
  records.alerts = [{ timestamp: 1000, status: 'RESOLVED' }, { timestamp: 'now', status: 'NEW' }]; records.trips = [{ startTime: 1000 }, {}];
  expect((await api.dashboard()).unhandledAlerts).toBe(1); expect((await api.alertTrend()).every(row => row.count === 0)).toBe(true);
  records.drivers = []; records.alerts = []; records.trips = []; expect((await api.dashboard()).totalDevices).toBe(1);
  sdk.getDocs.mockRejectedValue(new Error('offline')); expect((await api.dashboard()).alertsToday).toBe(0);
});
it('searches employees by name and phone', async () => {
  expect(await api.search('nguyễn')).toHaveLength(1); expect(await api.search('090')).toHaveLength(1); expect(await api.search('absent')).toEqual([]);
  records.drivers = [{ displayName: 'Bình' }]; expect(await api.search('bình')).toHaveLength(1);
});
it('documents local-only legacy adapters without claiming Firebase persistence', async () => {
  expect(await api.createDevice({ deviceCode: 'CAM', deviceName: 'Camera', deviceType: 'Android' })).toMatchObject({ deviceCode: 'CAM' });
  expect((await api.updateDevice('dev', { status: 'locked', deviceName: 'New' })).status).toBe('locked'); await api.updateDevice('dev', {});
  expect((await api.createBinding({ userId: 'u', deviceId: 'd' })).status).toBe('active'); expect((await api.unbindDevice('b')).status).toBe('ended');
  await api.updateVehicle('v', {}); await api.updateVehicle('v', { userId: 'u', displayName: 'Xe', licensePlate: '51A', vehicleType: 'truck' }); await api.deleteVehicle('v');
  expect(sdk.updateDoc).not.toHaveBeenCalled();
});
it('writes vehicle details to the correct driver document', async () => {
  await api.createVehicle({ userId: 'driver-1', displayName: 'Xe', licensePlate: '51B', vehicleType: 'truck' });
  expect(sdk.updateDoc).toHaveBeenCalledWith('drivers/driver-1', expect.objectContaining({ vehicleName: 'Xe', licensePlate: '51B' }));
  await api.createVehicle({ userId: '', displayName: 'Xe', licensePlate: null, vehicleType: null });
});
it('retains local settings, health and report placeholders', async () => {
  const [settings] = await api.detectionSettings(); expect(settings.earThreshold).toBe(0.22); expect(await api.saveDetectionSettings(settings)).toMatchObject({ earThreshold: 0.22 });
  expect((await api.deviceHealth())[0].status).toBe('connected'); expect(await api.auditLogs()).toEqual([]);
  expect(api.exportReportUrl()).toBe('#'); expect((await api.exportReport()).type).toBe('text/plain');
});
