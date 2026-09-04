import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './client';

const fetchMock = vi.fn<typeof fetch>();
function reply(body: unknown, status = 200) {
  fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(body), { status }));
}
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

describe('authentication and error handling', () => {
  it('sends URL-encoded login and saves token', async () => {
    reply({ access_token: 'jwt' });
    await api.login('admin+test', 'a&b');
    const [url, options] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/v1\/auth\/login$/);
    expect(options?.method).toBe('POST');
    expect(String(options?.body)).toBe('username=admin%2Btest&password=a%26b');
    expect(api.hasToken()).toBe(true);
    api.logout();
    expect(api.hasToken()).toBe(false);
  });
  it('does not save a token for wrong credentials', async () => {
    reply({ detail: 'Sai tài khoản hoặc mật khẩu' }, 400);
    await expect(api.login('admin', 'wrong')).rejects.toThrow('Sai tài khoản');
    expect(api.hasToken()).toBe(false);
  });
  it.each([{}, { access_token: '' }, { access_token: 123 }])('rejects malformed token response %j', async (body) => {
    reply(body);
    await expect(api.login('admin', 'pass')).rejects.toThrow('token');
    expect(api.hasToken()).toBe(false);
  });
  it('clears token and notifies UI on 401', async () => {
    sessionStorage.setItem('driverguard_token', 'expired');
    const listener = vi.fn();
    window.addEventListener('driverguard:unauthorized', listener, { once: true });
    reply({ detail: 'Expired' }, 401);
    await expect(api.devices()).rejects.toThrow('Expired');
    expect(listener).toHaveBeenCalledOnce();
    expect(api.hasToken()).toBe(false);
  });
  it('preserves token on forbidden response', async () => {
    sessionStorage.setItem('driverguard_token', 'jwt');
    reply({ detail: 'Forbidden' }, 403);
    await expect(api.users()).rejects.toThrow('Forbidden');
    expect(api.hasToken()).toBe(true);
  });
  it('formats FastAPI validation details', async () => {
    reply({ detail: [{ msg: 'Field required' }, {}] }, 422);
    await expect(api.users()).rejects.toThrow('Field required; Dữ liệu không hợp lệ');
  });
  it('handles non-JSON server errors', async () => {
    fetchMock.mockResolvedValueOnce(new Response('Server error', { status: 500 }));
    await expect(api.devices()).rejects.toThrow('HTTP 500');
  });
  it('handles unavailable network', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(api.dashboard()).rejects.toThrow('Không thể kết nối');
  });
  it('can issue requests without a stored token', async () => {
    reply([]);
    await api.users();
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).has('Authorization')).toBe(false);
  });
});

describe('API request contract', () => {
  const user = { full_name: 'An', phone: '0901234567' };
  const device = { deviceCode: 'CAM-2', deviceName: 'New', deviceType: 'edge-camera' };
  const vehicle = { userId: 'u1', displayName: 'Car', licensePlate: null, vehicleType: 'car' as const };
  it.each([
    ['summary', () => api.dashboard(), '/dashboard/summary', 'GET', undefined],
    ['trend', () => api.alertTrend(), '/dashboard/alert-trend?days=7', 'GET', undefined],
    ['custom trend', () => api.alertTrend(30), '/dashboard/alert-trend?days=30', 'GET', undefined],
    ['users', () => api.users(), '/users?role=driver', 'GET', undefined],
    ['create user', () => api.createUser(user), '/users', 'POST', { ...user, role: 'driver', is_active: true }],
    ['lock user', () => api.updateUser('u1', { is_active: false }), '/users/u1', 'PATCH', { is_active: false }],
    ['devices', () => api.devices(), '/devices', 'GET', undefined],
    ['create device', () => api.createDevice(device), '/devices', 'POST', device],
    ['lock device', () => api.updateDevice('d1', { status: 'locked' }), '/devices/d1', 'PATCH', { status: 'locked' }],
    ['vehicles', () => api.vehicles(), '/vehicles', 'GET', undefined],
    ['create vehicle', () => api.createVehicle(vehicle), '/vehicles', 'POST', vehicle],
    ['edit vehicle', () => api.updateVehicle('v1', { displayName: 'New' }), '/vehicles/v1', 'PATCH', { displayName: 'New' }],
    ['sessions', () => api.sessions(), '/monitoring-sessions', 'GET', undefined],
    ['session filters', () => api.sessions({ user_id: 'a b', status: '' }), '/monitoring-sessions?user_id=a+b', 'GET', undefined],
    ['events', () => api.events(), '/drowsiness-events?pageSize=100', 'GET', undefined],
    ['event filters', () => api.events({ pageSize: 20, page: 2, status: 'NEW', userId: undefined }), '/drowsiness-events?pageSize=20&page=2&status=NEW', 'GET', undefined],
    ['resolve event', () => api.updateEventStatus('e1', 'RESOLVED', 'Done'), '/drowsiness-events/e1/status', 'PATCH', { status: 'RESOLVED', note: 'Done' }],
    ['empty note', () => api.updateEventStatus('e1', 'ACKNOWLEDGED'), '/drowsiness-events/e1/status', 'PATCH', { status: 'ACKNOWLEDGED', note: null }],
  ] as const)('%s', async (_name, action, path, method, body) => {
    sessionStorage.setItem('driverguard_token', 'jwt');
    reply({ ok: true });
    await action();
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(`http://localhost:8000/api/v1${path}`);
    expect(init?.method || 'GET').toBe(method);
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer jwt');
    if (body) {
      expect(JSON.parse(String(init?.body))).toEqual(body);
      expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json');
    }
  });
  it('handles DELETE 204 without attempting JSON parsing', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(api.deleteVehicle('v1')).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][1]?.method).toBe('DELETE');
  });
  it('downloads CSV through authenticated request', async () => {
    sessionStorage.setItem('driverguard_token', 'jwt');
    fetchMock.mockResolvedValueOnce(new Response('id,status\ne1,NEW'));
    const blob = await api.exportReport();
    const text = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsText(blob);
    });
    expect(text).toContain('e1,NEW');
    expect(fetchMock.mock.calls[0][0]).toBe(api.exportReportUrl());
  });
});
