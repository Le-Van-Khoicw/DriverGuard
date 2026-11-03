import { useEffect, useState } from 'react';
import { Driver, qualificationLabel, Trip } from '../api/dispatch';

const statusLabels: Record<string, string> = {
  active: 'Hoạt động',
  pending: 'Chờ kích hoạt',
  locked: 'Đã khóa',
  rejected: 'Từ chối'
};

const statusColors: Record<string, { bg: string; color: string; border: string }> = {
  active: { bg: 'rgba(16, 185, 129, 0.1)', color: '#10b981', border: 'rgba(16, 185, 129, 0.25)' },
  pending: { bg: 'rgba(245, 158, 11, 0.1)', color: '#f59e0b', border: 'rgba(245, 158, 11, 0.25)' },
  locked: { bg: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', border: 'rgba(239, 68, 68, 0.25)' },
  rejected: { bg: 'rgba(107, 114, 128, 0.1)', color: '#6b7280', border: 'rgba(107, 114, 128, 0.25)' }
};

export default function DriverManagement({ drivers, trips, admin, busy, search, openProfile, saveAccount }: {
  drivers: Driver[]; trips: Trip[]; admin: boolean; busy: boolean; search: string;
  openProfile: (uid: string) => void; saveAccount: (data: Record<string, unknown>) => void;
}) {
  const filtered = drivers.filter(d => 
    JSON.stringify(d).toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi'))
  );

  return (
    <div className="log-table" style={{ marginTop: '8px' }}>
      <table>
        <thead>
          <tr>
            <th style={{ width: '24%' }}>Tài xế</th>
            <th style={{ width: '24%' }}>Liên hệ</th>
            <th style={{ width: '26%' }}>Hồ sơ nghề nghiệp</th>
            <th style={{ width: '26%' }}>Trạng thái tài khoản</th>
          </tr>
        </thead>
        <tbody>
          {filtered.map(d => {
            const activeTripsCount = trips.filter(t => t.driverId === d.id && !['completed', 'completed_issues', 'cancelled'].includes(t.status)).length;
            return (
              <tr key={d.id}>
                <td>
                  <strong style={{ fontSize: '14px', display: 'block' }}>{d.displayName}</strong>
                  <span style={{ fontSize: '12px', color: 'var(--muted)', fontWeight: 600 }}>
                    {d.employeeCode || 'Chưa cấp mã'}
                  </span>
                  {activeTripsCount > 0 && (
                    <small style={{ color: '#2563eb', display: 'block', marginTop: '2px' }}>
                      Đang chạy {activeTripsCount} chuyến
                    </small>
                  )}
                </td>

                <td>
                  <div style={{ fontSize: '13px', fontWeight: 500 }}>{d.phone || 'Chưa có SĐT'}</div>
                  <small style={{ color: 'var(--muted)', display: 'block' }}>{d.email}</small>
                </td>

                <td>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-start' }}>
                    <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                      <strong style={{ fontSize: '13px' }}>{qualificationLabel(d)}</strong>
                      {d.licenseClass && (
                        <span style={{ fontSize: '11px', background: 'var(--surface-variant, rgba(0,0,0,0.05))', padding: '2px 6px', borderRadius: '4px' }}>
                          GPLX {d.licenseClass}
                        </span>
                      )}
                    </div>
                    <button 
                      type="button" 
                      onClick={() => openProfile(d.id)}
                      style={{ padding: '4px 10px', fontSize: '12px', cursor: 'pointer' }}
                    >
                      {admin ? 'Xem / Duyệt hồ sơ' : 'Xem hồ sơ'}
                    </button>
                  </div>
                </td>

                <td>
                  <DriverInlineStatus driver={d} admin={admin} busy={busy} saveAccount={saveAccount} />
                </td>
              </tr>
            );
          })}
          {!filtered.length && (
            <tr>
              <td colSpan={4} style={{ textAlign: 'center', padding: '24px', color: 'var(--muted)' }}>
                Không tìm thấy tài xế nào phù hợp.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function DriverInlineStatus({ driver, admin, busy, saveAccount }: {
  driver: Driver; admin: boolean; busy: boolean; saveAccount: (data: Record<string, unknown>) => void;
}) {
  const [status, setStatus] = useState(driver.accountStatus);
  useEffect(() => { setStatus(driver.accountStatus); }, [driver.accountStatus]);

  const canActivate = driver.qualificationStatus === 'approved' && (driver.licenseExpiresAt || 0) > Date.now() && (driver.healthExpiresAt || 0) > Date.now();
  const isDirty = status !== driver.accountStatus;
  const blocked = status === 'active' && driver.accountStatus !== 'active' && !canActivate;
  const currentSt = statusColors[driver.accountStatus] || statusColors.rejected;

  if (!admin) {
    return (
      <span style={{ 
        display: 'inline-block',
        padding: '4px 10px', 
        borderRadius: '6px', 
        fontSize: '12px', 
        fontWeight: 'bold',
        background: currentSt.bg,
        color: currentSt.color,
        border: `1px solid ${currentSt.border}`
      }}>
        {statusLabels[driver.accountStatus] || driver.accountStatus}
      </span>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
      <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
        <select 
          value={status} 
          onChange={e => setStatus(e.target.value)}
          disabled={busy}
          style={{ padding: '6px 10px', borderRadius: '6px', fontSize: '13px', minWidth: '120px' }}
        >
          {driver.accountStatus === 'pending' && <option value="pending" disabled>Chờ kích hoạt</option>}
          <option value="active">Hoạt động</option>
          <option value="locked">Tạm khóa</option>
          <option value="rejected">Từ chối</option>
        </select>

        {isDirty && (
          <button 
            type="button"
            disabled={busy || blocked}
            onClick={() => saveAccount({ status, driverId: driver.id })}
            style={{ 
              padding: '6px 12px', 
              fontSize: '13px', 
              fontWeight: 'bold', 
              background: '#2563eb', 
              color: '#fff', 
              border: 'none', 
              borderRadius: '6px', 
              cursor: 'pointer' 
            }}
          >
            {busy ? '…' : 'Lưu'}
          </button>
        )}
      </div>

      {blocked && (
        <span style={{ fontSize: '11px', color: '#ef4444' }}>
          Cần duyệt hồ sơ trước khi kích hoạt
        </span>
      )}
    </div>
  );
}
