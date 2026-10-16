import { test, expect, type Page } from '@playwright/test';
import { mockFirebase } from './firebaseFixture';
async function login(page: Page) {
  await page.goto('/');
  await page.getByPlaceholder('Nhập username').fill('admin');
  await page.getByPlaceholder('Nhập mật khẩu').fill('admin123');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tổng quan hệ thống' })).toBeVisible();
}
async function navigate(page: Page, name: string) {
  if (await page.locator('.menu-button').isVisible()) await page.getByRole('button', { name: 'Mở menu' }).click();
  await page.locator('.sidebar nav').getByRole('button', { name: new RegExp(`^${name}`) }).click();
}
test.beforeEach(async ({ page }) => { await mockFirebase(page); });
test('login validation, failed credentials, reload and logout', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  expect(await page.getByPlaceholder('Nhập username').evaluate((input: HTMLInputElement) => input.validity.valid)).toBe(false);
  await page.getByPlaceholder('Nhập username').fill('admin');
  await page.getByPlaceholder('Nhập mật khẩu').fill('wrong');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByText('Mật khẩu phải từ 6 ký tự trở lên hoặc dùng tài khoản admin/admin123')).toBeVisible();
  await login(page); await page.reload();
  await expect(page.getByRole('heading', { name: 'Tổng quan hệ thống' })).toBeVisible();
  if (await page.locator('.menu-button').isVisible()) await page.getByRole('button', { name: 'Mở menu' }).click();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('driverguard_token'))).toBeNull();
});
test('all current pages render without runtime errors or horizontal overflow', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await login(page);
  for (const [nav, title] of [['Thiết bị', 'Thiết bị'], ['Gán thiết bị', 'Gán thiết bị'], ['Nhân viên', 'Quản lý nhân viên'], ['Phương tiện', 'Phương tiện'], ['Phiên giám sát', 'Phiên giám sát'], ['Cảnh báo', 'Sự kiện Cảnh báo Buồn ngủ'], ['Cấu hình AI', 'Cấu hình nhận diện'], ['Sức khỏe thiết bị', 'Tình trạng thiết bị'], ['Nhật ký', 'Nhật ký quản trị'], ['Điều hành vận tải', 'Điều hành vận tải'], ['Tổng quan', 'Tổng quan hệ thống']]) {
    await navigate(page, nav); await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  }
  await expect(page.locator('.recharts-surface').first()).toBeVisible();
  await page.screenshot({ path: test.info().outputPath('dashboard.png'), fullPage: true });
  expect(errors).toEqual([]);
});
test('rejects with a reason, reapproves, locks and unlocks employees through Firestore', async ({ page }) => {
  await login(page); await navigate(page, 'Nhân viên');
  const row = page.getByRole('row').filter({ hasText: 'pending@example.com' });
  await row.getByRole('button', { name: 'Từ chối', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Xác nhận từ chối' })).toBeDisabled();
  await page.getByLabel('Lý do từ chối').fill('Thiếu hồ sơ');
  await page.getByRole('button', { name: 'Xác nhận từ chối' }).click();
  await expect(row).toContainText('Thiếu hồ sơ');
  await row.getByRole('button', { name: 'Duyệt lại' }).click();
  await expect(row).toContainText('Hoạt động'); await expect(row).not.toContainText('Thiếu hồ sơ');
  await row.getByRole('button', { name: 'Khóa', exact: true }).click(); await expect(row).toContainText('Bị khóa');
  await row.getByRole('button', { name: 'Mở khóa', exact: true }).click(); await expect(row).toContainText('Hoạt động');
});
test('handles alerts and downloads the current report', async ({ page }) => {
  await login(page); await navigate(page, 'Cảnh báo');
  await page.getByRole('cell', { name: 'Buồn ngủ', exact: true }).click();
  await page.getByRole('combobox', { name: 'Trạng thái', exact: true }).selectOption('ACKNOWLEDGED');
  await page.getByLabel('Ghi chú của quản trị viên').fill('Đã gọi tài xế');
  await page.getByRole('button', { name: 'Lưu trạng thái xử lý' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('cell', { name: 'ACKNOWLEDGED', exact: true })).toBeVisible();
  const download = page.waitForEvent('download'); await page.getByRole('button', { name: 'Xuất CSV' }).click();
  expect((await download).suggestedFilename()).toBe('drowsiness-events.csv');
});
test('creates a multi-stop delivery and sends a reminder with realtime updates', async ({ page }) => {
  await login(page); await navigate(page, 'Điều hành vận tải');
  await page.getByLabel('Tên chuyến').fill('Chuyến TEST');
  await page.getByLabel('Nhân viên phụ trách').selectOption('driver-1');
  await page.getByLabel('Giờ xuất phát (Việt Nam)').fill('2026-10-20T08:00');
  await page.getByLabel('Địa chỉ', { exact: true }).fill('Điểm A');
  await page.getByRole('button', { name: 'Thêm điểm giao' }).click();
  await page.getByLabel('Địa chỉ', { exact: true }).nth(1).fill('Điểm B');
  await page.getByRole('button', { name: 'Tạo và phân công' }).click();
  await expect(page.getByRole('heading', { name: 'Chuyến TEST' })).toBeVisible();
  await expect(page.getByText('Tiến độ: 0/2 điểm giao')).toBeVisible();
  await page.getByRole('button', { name: 'Nhắc nhở', exact: true }).click();
  await page.getByLabel('Nhân viên').selectOption('driver-1');
  await page.getByLabel('Chuyến liên quan').selectOption({ label: 'Chuyến TEST' });
  await page.getByLabel('Nội dung').fill('Hãy nghỉ ngơi khi mệt');
  await page.getByRole('button', { name: 'Gửi nhắc nhở' }).click();
  await expect(page.getByText('Hãy nghỉ ngơi khi mệt', { exact: true })).toBeVisible();
  await expect(page.getByText(/Chưa xác nhận đã đọc/)).toBeVisible();
  await page.getByRole('button', { name: 'Điểm và xếp hạng' }).click();
  await expect(page.getByText('Chưa có điểm đã chốt trong kỳ.')).toBeVisible();
});
