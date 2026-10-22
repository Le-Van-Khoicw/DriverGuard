import { CompanyAccount, Staff } from '../api/dispatch';

type Props = {
  account: CompanyAccount | null;
  employee?: Staff;
  busy: boolean;
  chooseAccount: () => void;
  save: (data: Record<string, unknown>) => void;
};

export default function StaffAccessForm({ account, employee, busy, chooseAccount, save }: Props) {
  if (!account) return <p>Chọn tài khoản đã đăng ký để cấp hoặc sửa quyền. <button onClick={chooseAccount}>Chọn tài khoản</button></p>;
  const name = account.authDisplayName || account.email.split('@')[0] || account.displayName;
  return <>
    <p>Tên được lấy từ Google; nếu chưa có tên thì dùng phần trước @ của email. Mã nhân viên được cấp tự động lần đầu và giữ nguyên khi sửa quyền.</p>
    <form key={account.uid} className="log-staff-form" onSubmit={event => {
      event.preventDefault();
      const data = new FormData(event.currentTarget);
      // Only editable permissions/contact are sent. The server resolves the name and code itself.
      save({ staffId: account.uid, phone: String(data.get('phone') || ''), role: data.get('role'), active: data.get('active') === 'true' });
    }}>
      <label>Tài khoản<input value={account.email || account.uid} readOnly /></label>
      <label>Tên nhân viên (tự động)<input value={name} readOnly /></label>
      <label>Mã nhân viên (tự động)<input value={employee?.employeeCode || account.employeeCode || ''} placeholder="Được cấp khi lưu quyền lần đầu" readOnly /></label>
      <label>SĐT nhân viên<input name="phone" type="tel" defaultValue={employee?.phone ?? account.staffPhone ?? ''} placeholder="VD: 0901234567" maxLength={30} /></label>
      <label>Vai trò<select name="role" defaultValue={employee?.role || account.staffRole || 'dispatcher'}><option value="dispatcher">Điều phối viên</option><option value="admin">Quản trị</option></select></label>
      <label>Quyền truy cập<select name="active" defaultValue={String(employee?.active ?? (account.staffRole ? account.staffActive : true))}><option value="true">Hoạt động</option><option value="false">Thu hồi / khóa</option></select></label>
      <button disabled={busy}>Lưu quyền nhân viên</button>
      <button type="button" onClick={chooseAccount} disabled={busy}>Chọn tài khoản khác</button>
    </form>
  </>;
}
