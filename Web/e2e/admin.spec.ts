import { test, expect, type Page } from '@playwright/test';
import { fixtures } from '../src/test/fixtures';

async function mockBackend(page: Page) {
  const data = fixtures();
  let bindings: Array<{ id: string; userId: string; deviceId: string; status: string; boundAt: string; unboundAt: string | null }> = [{ id: 'binding-1', userId: 'user-1', deviceId: 'device-1', status: 'active', boundAt: data.sessions[0].startedAt, unboundAt: null }];
  let settings = [{ id: 'setting-1', deviceId: null, earThreshold: 0.25, confidenceThreshold: 0.8, closedDurationThresholdMs: 2000, updatedAt: data.sessions[0].startedAt }];
  const health = [{ id: 'health-1', deviceId: 'device-1', status: 'connected', lastHeartbeatAt: data.sessions[0].startedAt, note: null, createdAt: data.sessions[0].startedAt }];
  const audits = [{ id: 'audit-1', adminId: 'admin-1', action: 'update_device', targetTable: 'devices', targetId: 'device-1', beforeValue: { status: 'offline' }, afterValue: { status: 'online' }, createdAt: data.sessions[0].startedAt }];
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/auth/login') {
      const form = new URLSearchParams(request.postData() || '');
      return route.fulfill({ status: form.get('password') === 'wrong' ? 400 : 200, json: form.get('password') === 'wrong' ? { detail: 'Sai tài khoản hoặc mật khẩu' } : { access_token: 'test-token' } });
    }
    if (request.headers().authorization !== 'Bearer test-token') return route.fulfill({ status: 401, json: { detail: 'Unauthorized' } });
    if (path === '/reports/export') return route.fulfill({ contentType: 'text/csv', body: 'id,status\nevent-1,NEW', headers: { 'Content-Disposition': 'attachment; filename=report.csv' } });
    if (request.method() === 'POST' && path === '/devices') {
      const device = { ...data.devices[0], ...request.postDataJSON(), id: 'device-2' };
      data.devices.push(device); return route.fulfill({ json: device });
    }
    if (request.method() === 'POST' && path === '/users') {
      const payload = request.postDataJSON();
      const user = { ...data.users[0], id: 'user-2', fullName: payload.full_name, phone: payload.phone };
      data.users.push(user); return route.fulfill({ json: user });
    }
    if (request.method() === 'POST' && path === '/vehicles') {
      const vehicle = { ...data.vehicles[0], ...request.postDataJSON(), id: 'vehicle-2' };
      data.vehicles.push(vehicle); return route.fulfill({ json: vehicle });
    }
    if (request.method() === 'POST' && path === '/device-bindings') {
      bindings = bindings.map(item => ({ ...item, status: 'ended', unboundAt: data.sessions[0].startedAt }));
      const binding = { id: 'binding-2', ...request.postDataJSON(), status: 'active', boundAt: data.sessions[0].startedAt, unboundAt: null };
      bindings.push(binding); return route.fulfill({ json: binding });
    }
    if (request.method() === 'POST' && path === '/detection-settings') {
      const payload = request.postDataJSON(); const existing = settings.find(item => item.deviceId === payload.deviceId);
      const setting = { ...(existing || { id: 'setting-2' }), ...payload, updatedAt: data.sessions[0].startedAt };
      settings = existing ? settings.map(item => item.id === existing.id ? setting : item) : [...settings, setting];
      return route.fulfill({ json: setting });
    }
    if (request.method() === 'PATCH' && path.startsWith('/devices/')) {
      const device = data.devices.find(d => path.endsWith(d.id));
      Object.assign(device!, request.postDataJSON()); return route.fulfill({ json: device });
    }
    if (request.method() === 'PATCH' && path.startsWith('/users/')) {
      const user = data.users.find(u => path.endsWith(u.id))!;
      user.isActive = request.postDataJSON().is_active; return route.fulfill({ json: user });
    }
    if (request.method() === 'PATCH' && path.endsWith('/status')) {
      Object.assign(data.events[0], request.postDataJSON()); return route.fulfill({ json: data.events[0] });
    }
    if (request.method() === 'PATCH' && path.endsWith('/unbind')) {
      const binding = bindings.find(item => path.includes(item.id))!; Object.assign(binding, { status: 'ended', unboundAt: data.sessions[0].startedAt });
      return route.fulfill({ json: binding });
    }
    if (request.method() === 'DELETE' && path.startsWith('/vehicles/')) {
      data.vehicles = data.vehicles.filter(v => !path.endsWith(v.id));
      return route.fulfill({ status: 204 });
    }
    if (request.method() !== 'GET') throw new Error(`Unexpected mutation ${path}`);
    const responses: Record<string, unknown> = {
      '/dashboard/summary': data.summary, '/dashboard/alert-trend': data.trend, '/dashboard/recent-alerts': data.events,
      '/users': data.users, '/devices': data.devices, '/vehicles': data.vehicles,
      '/monitoring-sessions': data.sessions,
      '/drowsiness-events': { items: data.events, total: 1, page: 1, pageSize: 100 },
      '/device-bindings': bindings, '/detection-settings': settings, '/device-health': health, '/audit-logs': audits,
      '/search': [{ type: 'device', id: 'device-1', title: 'CAM-001', subtitle: 'Camera An' }],
    };
    if (!(path in responses)) throw new Error(`Unexpected API request ${path}`);
    return route.fulfill({ json: responses[path] });
  });
}
async function login(page: Page) {
  await page.goto('/');
  await page.getByPlaceholder('Nhập username').fill('admin');
  await page.getByPlaceholder('Nhập mật khẩu').fill('demo-test-only');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tổng quan hệ thống' })).toBeVisible();
}
async function navigate(page: Page, name: string) {
  if (await page.locator('.menu-button').isVisible()) await page.locator('.menu-button').click();
  await page.getByRole('navigation').getByRole('button', { name: new RegExp(`^${name}`) }).click();
}
test.beforeEach(async ({ page }) => { await mockBackend(page); });

test('login validation, wrong password, successful login, reload and logout', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  expect(await page.getByPlaceholder('Nhập username').evaluate((input: HTMLInputElement) => input.validity.valid)).toBe(false);
  await page.getByPlaceholder('Nhập username').fill('admin');
  await page.getByPlaceholder('Nhập mật khẩu').fill('wrong');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByText('Sai tài khoản hoặc mật khẩu')).toBeVisible();
  await login(page);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Tổng quan hệ thống' })).toBeVisible();
  if (await page.locator('.menu-button').isVisible()) await page.locator('.menu-button').click();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('driverguard_token'))).toBeNull();
});
test('all pages render without runtime errors or page-level horizontal overflow', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await login(page);
  for (const [nav, title] of [['Thiết bị', 'Thiết bị'], ['Gán thiết bị', 'Gán thiết bị'], ['Tài xế', 'Tài xế'], ['Phương tiện', 'Phương tiện'], ['Phiên giám sát', 'Phiên giám sát'], ['Cảnh báo', 'Cảnh báo buồn ngủ'], ['Cấu hình AI', 'Cấu hình nhận diện'], ['Sức khỏe thiết bị', 'Tình trạng thiết bị'], ['Nhật ký', 'Nhật ký quản trị'], ['Tổng quan', 'Tổng quan hệ thống']]) {
    await navigate(page, nav);
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  }
  await expect(page.locator('.recharts-surface').first()).toBeVisible();
  await page.screenshot({ path: test.info().outputPath('dashboard.png'), fullPage: true });
  expect(errors).toEqual([]);
});
test('create device and driver send correct API payloads', async ({ page }) => {
  await login(page); await navigate(page, 'Thiết bị');
  await page.getByRole('button', { name: 'Thêm thiết bị' }).click();
  await page.getByLabel('Mã thiết bị').fill('CAM-002'); await page.getByLabel('Tên thiết bị').fill('Camera mới');
  const deviceRequest = page.waitForRequest(r => r.url().endsWith('/devices') && r.method() === 'POST');
  await page.getByRole('button', { name: 'Thêm mới' }).click();
  expect((await deviceRequest).postDataJSON()).toMatchObject({ deviceCode: 'CAM-002', deviceName: 'Camera mới' });
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await navigate(page, 'Tài xế'); await page.getByRole('button', { name: 'Thêm tài xế' }).click();
  await page.getByLabel('Họ và tên').fill('Bình');
  const driverRequest = page.waitForRequest(r => new URL(r.url()).pathname.endsWith('/users') && r.method() === 'POST');
  await page.getByRole('button', { name: 'Thêm mới' }).click();
  expect((await driverRequest).postDataJSON()).toMatchObject({ full_name: 'Bình', role: 'driver', is_active: true });
});
test('alert handling and CSV download', async ({ page }) => {
  await login(page); await navigate(page, 'Cảnh báo');
  await page.getByText('DROWSINESS', { exact: true }).click();
  await page.getByRole('combobox', { name: 'Trạng thái', exact: true }).selectOption('ACKNOWLEDGED');
  await page.getByLabel('Ghi chú').fill('Đã gọi tài xế');
  const update = page.waitForRequest(r => r.url().endsWith('/event-1/status'));
  await page.getByRole('button', { name: 'Lưu xử lý' }).click();
  expect((await update).postDataJSON()).toEqual({ status: 'ACKNOWLEDGED', note: 'Đã gọi tài xế' });
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('cell', { name: 'ACKNOWLEDGED', exact: true })).toBeVisible();
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Xuất CSV' }).click();
  expect((await download).suggestedFilename()).toBe('drowsiness-events.csv');
});
test('lock/unlock resources, create and delete vehicle, switch theme', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: 'Đổi giao diện sáng tối' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await navigate(page, 'Thiết bị');
  await page.getByRole('button', { name: 'Khóa', exact: true }).click();
  await expect(page.getByRole('cell', { name: 'locked', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Mở khóa', exact: true }).click();
  await expect(page.getByRole('cell', { name: 'offline', exact: true })).toBeVisible();
  await navigate(page, 'Tài xế'); await page.getByRole('button', { name: 'Khóa', exact: true }).click();
  await expect(page.getByText('Tạm khóa', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Mở khóa', exact: true }).click();
  await expect(page.getByText('Hoạt động', { exact: true })).toBeVisible();
  await navigate(page, 'Phương tiện'); await page.getByRole('button', { name: 'Thêm phương tiện' }).click();
  await page.getByLabel('Tên phương tiện').fill('Xe mới');
  await page.getByRole('button', { name: 'Thêm mới' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByText('Xe mới', { exact: true })).toBeVisible();
  page.once('dialog', dialog => dialog.dismiss());
  const row = page.getByRole('row').filter({ hasText: 'Xe mới' });
  await row.getByRole('button', { name: 'Xóa' }).click(); await expect(row).toBeVisible();
  page.once('dialog', dialog => dialog.accept());
  await row.getByRole('button', { name: 'Xóa' }).click(); await expect(row).toHaveCount(0);
});
test('expired token redirects to login', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('driverguard_token', 'expired'));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeVisible();
});
test('network failure is visible and retry recovers', async ({ page }) => {
  await page.route('**/api/v1/dashboard/summary', route => route.abort('failed'));
  await login(page);
  await expect(page.getByText('Không thể tải dữ liệu Backend')).toBeVisible();
  await page.unroute('**/api/v1/dashboard/summary');
  await page.getByRole('button', { name: 'Thử lại' }).click();
  await expect(page.getByText('Không thể tải dữ liệu Backend')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Tổng quan hệ thống' })).toBeVisible();
});
test('device binding, AI settings, heartbeat, audit and global search', async ({ page }) => {
  await login(page);
  await navigate(page, 'Gán thiết bị');
  await expect(page.getByRole('cell', { name: 'Camera An', exact: true })).toBeVisible();
  await page.getByRole('main').getByRole('button', { name: 'Gán thiết bị', exact: true }).click();
  const bindingRequest = page.waitForRequest(r => r.url().endsWith('/device-bindings') && r.method() === 'POST');
  await page.getByRole('button', { name: 'Xác nhận gán' }).click();
  expect((await bindingRequest).postDataJSON()).toEqual({ userId: 'user-1', deviceId: 'device-1' });
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: /Hủy gán/ }).last().click();
  await expect(page.getByRole('button', { name: /Hủy gán/ })).toHaveCount(0);
  await navigate(page, 'Cấu hình AI');
  await page.getByLabel('Ngưỡng EAR').fill('0.3');
  const settingsRequest = page.waitForRequest(r => r.url().endsWith('/detection-settings') && r.method() === 'POST');
  await page.getByRole('button', { name: /Lưu cấu hình/ }).click();
  expect((await settingsRequest).postDataJSON()).toMatchObject({ deviceId: null, earThreshold: .3 });
  await expect(page.getByText('Đã lưu cấu hình nhận diện')).toBeVisible();
  await navigate(page, 'Sức khỏe thiết bị'); await expect(page.getByText('connected')).toBeVisible();
  await page.getByLabel('Lọc thiết bị').selectOption('device-1');
  await expect(page.getByRole('cell', { name: 'Camera An', exact: true })).toBeVisible();
  await navigate(page, 'Nhật ký'); await page.getByRole('button', { name: 'Chi tiết' }).click();
  await expect(page.getByRole('dialog', { name: 'Chi tiết thay đổi' })).toContainText('offline');
  await page.getByRole('button', { name: 'Đóng hộp thoại' }).click();
  await page.getByLabel('Tìm kiếm toàn hệ thống').fill('CAM-001');
  const searchRequest = page.waitForRequest(r => r.url().includes('/search?q=CAM-001'));
  await page.getByLabel('Thực hiện tìm kiếm').click();
  await searchRequest; await expect(page.getByRole('heading', { name: 'Kết quả tìm kiếm' })).toBeVisible(); await expect(page.getByText('CAM-001', { exact: true })).toBeVisible();
});
