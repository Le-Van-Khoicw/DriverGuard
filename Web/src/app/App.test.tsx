import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import App from './App';
import { api } from '../api/client';
import { fixtures } from '../test/fixtures';

vi.mock('../api/client', () => ({ api: {
  hasToken: vi.fn(), login: vi.fn(), logout: vi.fn(), dashboard: vi.fn(), alertTrend: vi.fn(),
  users: vi.fn(), devices: vi.fn(), vehicles: vi.fn(), sessions: vi.fn(), events: vi.fn(),
  createUser: vi.fn(), updateUser: vi.fn(), createDevice: vi.fn(), updateDevice: vi.fn(),
  createVehicle: vi.fn(), deleteVehicle: vi.fn(), updateEventStatus: vi.fn(), exportReport: vi.fn(),
} }));
// jsdom has no layout engine. Real chart rendering is checked by Playwright.
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  AreaChart: () => <div data-testid="chart" />,
  Area: () => null, CartesianGrid: () => null, Tooltip: () => null, XAxis: () => null, YAxis: () => null,
}));
const mock = vi.mocked(api);
beforeEach(() => {
  vi.resetAllMocks();
  const data = fixtures();
  mock.hasToken.mockReturnValue(true);
  mock.dashboard.mockResolvedValue(data.summary);
  mock.alertTrend.mockResolvedValue(data.trend);
  mock.users.mockResolvedValue(data.users);
  mock.devices.mockResolvedValue(data.devices);
  mock.vehicles.mockResolvedValue(data.vehicles);
  mock.sessions.mockResolvedValue(data.sessions);
  mock.events.mockResolvedValue({ items: data.events, total: 1, page: 1, pageSize: 100 });
});
async function openPage(name?: string) {
  const user = userEvent.setup();
  render(<App />);
  await screen.findByRole('heading', { name: 'Tổng quan hệ thống' });
  if (name) await user.click(within(screen.getByRole('navigation')).getByRole('button', { name: new RegExp(`^${name}`) }));
  return user;
}

it('shows login without a token; toggles password and logs in', async () => {
  mock.hasToken.mockReturnValue(false);
  const user = userEvent.setup(); render(<App />);
  const password = screen.getByPlaceholderText('Nhập mật khẩu');
  expect(password).toHaveAttribute('type', 'password');
  await user.click(screen.getByRole('button', { name: 'Hiện hoặc ẩn mật khẩu' }));
  expect(password).toHaveAttribute('type', 'text');
  await user.type(screen.getByPlaceholderText('Nhập username'), ' admin ');
  await user.type(password, 'secret');
  await user.click(screen.getByRole('button', { name: 'Đăng nhập' }));
  expect(mock.login).toHaveBeenCalledWith('admin', 'secret');
  await screen.findByRole('heading', { name: 'Tổng quan hệ thống' });
});
it('displays login failure without entering dashboard', async () => {
  mock.hasToken.mockReturnValue(false); mock.login.mockRejectedValue(new Error('Sai mật khẩu'));
  const user = userEvent.setup(); render(<App />);
  await user.type(screen.getByPlaceholderText('Nhập username'), 'admin');
  await user.type(screen.getByPlaceholderText('Nhập mật khẩu'), 'wrong');
  await user.click(screen.getByRole('button', { name: 'Đăng nhập' }));
  expect(await screen.findByText('Sai mật khẩu')).toBeVisible();
  expect(mock.dashboard).not.toHaveBeenCalled();
});
it('prevents submitting empty login fields', async () => {
  mock.hasToken.mockReturnValue(false); const user = userEvent.setup(); render(<App />);
  await user.click(screen.getByRole('button', { name: 'Đăng nhập' }));
  expect(mock.login).not.toHaveBeenCalled();
});
it('logs out and returns to login', async () => {
  const user = await openPage(); await user.click(screen.getByRole('button', { name: 'Đăng xuất' }));
  expect(mock.logout).toHaveBeenCalledOnce();
  expect(screen.getByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeVisible();
});
it('returns to login on expired session notification', async () => {
  await openPage(); act(() => window.dispatchEvent(new Event('driverguard:unauthorized')));
  expect(screen.getByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeVisible();
});
it('renders summary, chart and recent alert details', async () => {
  const user = await openPage();
  expect(screen.getByText('Tổng thiết bị')).toBeVisible(); expect(screen.getByTestId('chart')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /DROWSINESS/ }));
  expect(screen.getByRole('dialog', { name: 'Chi tiết cảnh báo' })).toBeVisible();
  expect(screen.getByText('Không có ảnh minh chứng')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Đóng hộp thoại' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});
it('shows load failure and retries successfully', async () => {
  mock.dashboard.mockRejectedValueOnce(new Error('Network offline'));
  const user = await openPage(); expect(screen.getByText('Network offline')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Thử lại' }));
  await screen.findByTestId('chart'); expect(screen.queryByText('Network offline')).not.toBeInTheDocument();
});
it('shows empty dashboard and empty resource pages', async () => {
  mock.users.mockResolvedValue([]); mock.devices.mockResolvedValue([]); mock.sessions.mockResolvedValue([]);
  mock.vehicles.mockResolvedValue([]); mock.events.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 });
  const user = await openPage(); expect(screen.getByText('Chưa có cảnh báo')).toBeVisible();
  for (const [name, text] of [['Thiết bị', 'Không tìm thấy thiết bị'], ['Tài xế', 'Không tìm thấy tài xế'], ['Phương tiện', 'Không tìm thấy phương tiện'], ['Phiên giám sát', 'Chưa có phiên giám sát'], ['Cảnh báo', 'Không tìm thấy cảnh báo']]) {
    await user.click(within(screen.getByRole('navigation')).getByRole('button', { name: new RegExp(`^${name}`) }));
    expect(screen.getByText(text)).toBeVisible();
    if (name === 'Phương tiện') expect(screen.getByRole('button', { name: 'Thêm phương tiện' })).toBeDisabled();
  }
});
it('searches devices, creates and locks a device', async () => {
  const user = await openPage('Thiết bị');
  await user.type(screen.getByPlaceholderText('Tìm kiếm...'), 'unknown');
  expect(screen.getByText('Không tìm thấy thiết bị')).toBeVisible();
  await user.clear(screen.getByPlaceholderText('Tìm kiếm...'));
  await user.click(screen.getByRole('button', { name: 'Khóa' }));
  expect(mock.updateDevice).toHaveBeenCalledWith('device-1', { status: 'locked' });
  await screen.findByRole('button', { name: 'Thêm thiết bị' });
  await user.click(screen.getByRole('button', { name: 'Thêm thiết bị' }));
  await user.type(screen.getByLabelText('Mã thiết bị'), 'CAM-002');
  await user.type(screen.getByLabelText('Tên thiết bị'), 'Camera mới');
  await user.click(screen.getByRole('button', { name: 'Thêm mới' }));
  expect(mock.createDevice).toHaveBeenCalledWith({ deviceCode: 'CAM-002', deviceName: 'Camera mới', deviceType: 'edge-camera' });
});
it('keeps device dialog open on duplicate code error', async () => {
  mock.createDevice.mockRejectedValue(new Error('Mã thiết bị đã được đăng ký'));
  const user = await openPage('Thiết bị'); await user.click(screen.getByRole('button', { name: 'Thêm thiết bị' }));
  await user.type(screen.getByLabelText('Mã thiết bị'), 'CAM-001'); await user.type(screen.getByLabelText('Tên thiết bị'), 'Duplicate');
  await user.click(screen.getByRole('button', { name: 'Thêm mới' }));
  expect(await screen.findByText('Mã thiết bị đã được đăng ký')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Hủy' })); expect(screen.queryByRole('dialog')).toBeNull();
});
it('unlocks locked device', async () => {
  mock.devices.mockResolvedValue([{ ...fixtures().devices[0], status: 'locked' }]);
  const user = await openPage('Thiết bị'); await user.click(screen.getByRole('button', { name: 'Mở khóa' }));
  expect(mock.updateDevice).toHaveBeenCalledWith('device-1', { status: 'offline' });
});
it('reports device update failure', async () => {
  mock.updateDevice.mockRejectedValue(new Error('Update failed')); const user = await openPage('Thiết bị');
  await user.click(screen.getByRole('button', { name: 'Khóa' }));
  expect(await screen.findByText('Update failed')).toBeVisible();
});
it('creates, searches and locks a driver', async () => {
  const user = await openPage('Tài xế'); await user.type(screen.getByPlaceholderText('Tìm kiếm...'), '0901234567');
  expect(screen.getByText('Nguyễn An')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Khóa' }));
  expect(mock.updateUser).toHaveBeenCalledWith('user-1', { is_active: false });
  await screen.findByRole('button', { name: 'Thêm tài xế' }); await user.click(screen.getByRole('button', { name: 'Thêm tài xế' }));
  await user.type(screen.getByLabelText('Họ và tên'), 'Bình'); await user.type(screen.getByLabelText('Số điện thoại'), '0909999999');
  await user.click(screen.getByRole('button', { name: 'Thêm mới' }));
  expect(mock.createUser).toHaveBeenCalledWith({ full_name: 'Bình', phone: '0909999999' });
});
it('handles driver creation and update errors', async () => {
  mock.updateUser.mockRejectedValue(new Error('Cannot lock')); mock.createUser.mockRejectedValue(new Error('Duplicate phone'));
  const user = await openPage('Tài xế'); await user.click(screen.getByRole('button', { name: 'Khóa' }));
  expect(await screen.findByText('Cannot lock')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Thêm tài xế' })); await user.type(screen.getByLabelText('Họ và tên'), 'Bình');
  await user.click(screen.getByRole('button', { name: 'Thêm mới' })); expect(await screen.findByText('Duplicate phone')).toBeVisible();
});
it('creates a vehicle linked to a driver', async () => {
  const user = await openPage('Phương tiện'); await user.click(screen.getByRole('button', { name: 'Thêm phương tiện' }));
  await user.type(screen.getByLabelText('Tên phương tiện'), 'Xe mới'); await user.type(screen.getByLabelText('Biển số'), '51B-00001');
  await user.selectOptions(screen.getByLabelText('Loại xe'), 'truck');
  await user.click(screen.getByRole('button', { name: 'Thêm mới' }));
  expect(mock.createVehicle).toHaveBeenCalledWith({ userId: 'user-1', displayName: 'Xe mới', licensePlate: '51B-00001', vehicleType: 'truck' });
});
it('requires confirmation before deleting vehicle', async () => {
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false); const user = await openPage('Phương tiện');
  await user.click(screen.getByRole('button', { name: 'Xóa' })); expect(mock.deleteVehicle).not.toHaveBeenCalled();
  confirm.mockReturnValue(true); await user.click(screen.getByRole('button', { name: 'Xóa' }));
  expect(mock.deleteVehicle).toHaveBeenCalledWith('vehicle-1');
});
it('shows vehicle deletion and creation errors', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true); mock.deleteVehicle.mockRejectedValue(new Error('Cannot delete'));
  mock.createVehicle.mockRejectedValue(new Error('Cannot create')); const user = await openPage('Phương tiện');
  await user.click(screen.getByRole('button', { name: 'Xóa' })); expect(await screen.findByText('Cannot delete')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Thêm phương tiện' })); await user.type(screen.getByLabelText('Tên phương tiện'), 'New');
  await user.click(screen.getByRole('button', { name: 'Thêm mới' })); expect(await screen.findByText('Cannot create')).toBeVisible();
});
it('shows session linked names and supports search', async () => {
  const user = await openPage('Phiên giám sát');
  expect(screen.getByText('CAM-001')).toBeVisible(); expect(screen.getByText('51A-12345')).toBeVisible();
  await user.type(screen.getByPlaceholderText('Tìm kiếm...'), 'nobody'); expect(screen.getByText('Chưa có phiên giám sát')).toBeVisible();
});
it('filters alerts and saves handling status with note', async () => {
  const user = await openPage('Cảnh báo'); await user.selectOptions(screen.getByRole('combobox'), 'RESOLVED');
  expect(screen.getByText('Không tìm thấy cảnh báo')).toBeVisible(); await user.selectOptions(screen.getByRole('combobox'), 'NEW');
  await user.click(screen.getByText('DROWSINESS')); await user.selectOptions(screen.getByLabelText('Trạng thái'), 'ACKNOWLEDGED');
  await user.type(screen.getByLabelText('Ghi chú'), 'Đã liên hệ'); await user.click(screen.getByRole('button', { name: 'Lưu xử lý' }));
  expect(mock.updateEventStatus).toHaveBeenCalledWith('event-1', 'ACKNOWLEDGED', 'Đã liên hệ');
});
it('shows evidence image and handling failure', async () => {
  mock.events.mockResolvedValue({ items: [{ ...fixtures().events[0], imageUrl: '/evidence.jpg' }], total: 1, page: 1, pageSize: 100 });
  mock.updateEventStatus.mockRejectedValue(new Error('Cannot resolve')); const user = await openPage('Cảnh báo');
  await user.click(screen.getByText('DROWSINESS')); expect(screen.getByAltText('Ảnh minh chứng cảnh báo')).toHaveAttribute('src', '/evidence.jpg');
  await user.click(screen.getByRole('button', { name: 'Lưu xử lý' })); expect(await screen.findByText('Cannot resolve')).toBeVisible();
});
it('downloads CSV and revokes the object URL', async () => {
  const blob = new Blob(['id,status']); mock.exportReport.mockResolvedValue(blob);
  const create = vi.fn(() => 'blob:test'); const revoke = vi.fn();
  vi.stubGlobal('URL', Object.assign(class extends URL {}, { createObjectURL: create, revokeObjectURL: revoke }));
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  const user = await openPage('Cảnh báo'); await user.click(screen.getByRole('button', { name: 'Xuất CSV' }));
  expect(create).toHaveBeenCalledWith(blob); expect(click).toHaveBeenCalledOnce(); expect(revoke).toHaveBeenCalledWith('blob:test');
});
it('reports CSV failure instead of silently ignoring it', async () => {
  mock.exportReport.mockRejectedValue(new Error('Report failed')); const alert = vi.spyOn(window, 'alert').mockImplementation(() => {});
  const user = await openPage('Cảnh báo'); await user.click(screen.getByRole('button', { name: 'Xuất CSV' }));
  expect(alert).toHaveBeenCalledWith('Report failed');
});
