from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import Optional

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.device import Device
from app.schemas.device import DeviceCreate, DeviceUpdate, DeviceOut
from app.services.audit import log_action

router = APIRouter(prefix="/devices", tags=["devices"])


def _to_out(d: Device) -> DeviceOut:
    return DeviceOut(
        id=d.id,
        deviceCode=d.device_code,
        deviceName=d.device_name,
        deviceType=d.device_type,
        status=d.status,
        firmwareVersion=d.firmware_version,
        aiModelVersion=d.ai_model_version,
        lastSeenAt=d.last_seen_at,
        createdAt=d.created_at,
        updatedAt=d.updated_at,
    )


@router.get("", response_model=list[DeviceOut])
def list_devices(
    status: Optional[str] = None,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    query = db.query(Device)
    if status:
        query = query.filter(Device.status == status)
    devices = query.order_by(Device.created_at.desc()).all()
    return [_to_out(d) for d in devices]


@router.get("/{device_id}", response_model=DeviceOut)
def get_device(
    device_id: str,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    device = db.query(Device).filter(Device.id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Không tìm thấy thiết bị")
    return _to_out(device)


@router.post("", response_model=DeviceOut)
def create_device(
    payload: DeviceCreate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    """Thêm thiết bị bằng mã kích hoạt/QR — deviceCode chính là mã đó."""
    existing = db.query(Device).filter(Device.device_code == payload.deviceCode).first()
    if existing:
        raise HTTPException(status_code=400, detail="Mã thiết bị đã được đăng ký")

    device = Device(
        device_code=payload.deviceCode,
        device_name=payload.deviceName,
        device_type=payload.deviceType,
        status="offline",  # mặc định khi mới thêm, chưa có heartbeat
    )
    db.add(device)
    db.commit()
    db.refresh(device)
    return _to_out(device)


@router.patch("/{device_id}", response_model=DeviceOut)
def update_device(
    device_id: str,
    payload: DeviceUpdate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    """Dùng để đổi tên hoặc khóa/mở khóa thiết bị (status = 'locked' / 'offline')."""
    device = db.query(Device).filter(Device.id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Không tìm thấy thiết bị")

    before = {"deviceName": device.device_name, "status": device.status}

    if payload.deviceName is not None:
        device.device_name = payload.deviceName
    if payload.status is not None:
        device.status = payload.status

    db.commit()
    db.refresh(device)

    log_action(
        db, admin_id=current_admin.id, action="update_device",
        target_table="devices", target_id=device.id,
        before_value=before,
        after_value={"deviceName": device.device_name, "status": device.status},
    )
    return _to_out(device)