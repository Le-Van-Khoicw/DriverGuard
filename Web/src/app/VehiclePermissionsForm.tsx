import { useEffect, useState } from 'react';
import { Driver, DriverDossier, FleetVehicle } from '../api/dispatch';

export default function VehiclePermissionsForm({ driver, dossier, vehicles, busy, save }: {
  driver: Driver; dossier: DriverDossier; vehicles: FleetVehicle[]; busy: boolean;
  save: (data: Record<string, unknown>) => void;
}) {
  const [selected, setSelected] = useState<string[]>(driver.approvedVehicleIds || []);
  const [verified, setVerified] = useState(false);
  const savedIds = JSON.stringify(driver.approvedVehicleIds || []);
  useEffect(() => { setSelected(JSON.parse(savedIds)); setVerified(false); }, [driver.id, savedIds, dossier.revision]);
  return <form className="physical-dossier-form" onSubmit={e => {
    e.preventDefault();
    if (verified) save({ driverId: driver.id, revision: dossier.revision, vehicleIds: selected, verified });
  }}>
    <h3>Xe tài xế được phép lái</h3>
    <p>Xe mới cần được quản lý xác nhận phù hợp với GPLX hạng {driver.licenseClass || dossier.licenseClass}. Không cần nộp lại hồ sơ.</p>
    <div className="vehicle-permission-options">{vehicles.map(vehicle => <label className="review-confirm" key={vehicle.id}>
      <input type="checkbox" disabled={busy} checked={selected.includes(vehicle.id)} onChange={() => {
        setVerified(false); setSelected(old => old.includes(vehicle.id) ? old.filter(id => id !== vehicle.id) : [...old, vehicle.id]);
      }} />{vehicle.plate}{vehicle.name ? ` · ${vehicle.name}` : ''} · {vehicle.capacityKg} kg
    </label>)}</div>
    <label className="review-confirm"><input type="checkbox" checked={verified} disabled={busy} onChange={e => setVerified(e.target.checked)} />Tôi đã đối chiếu hạng GPLX và xác nhận tài xế được lái các xe đã chọn.</label>
    <button disabled={busy || !verified || !selected.length}>Cập nhật xe được phép lái</button>
  </form>;
}
