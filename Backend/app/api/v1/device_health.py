from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import Optional

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.device_health import DeviceHealth
from app.models.device import Device
from app.schemas.device_health import DeviceHeartbeat, DeviceHealthOut

router = APIRouter(prefix="/device-health", tags=["device-health"])


def _to_out(h: DeviceHealth) -> DeviceHealthOut:
    return DeviceHealthOut(
        id=h.id,
        deviceId=h.device_id,
        status=h.status,
        lastHeartbeatAt=h.last_heartbeat_at,
        note=h.note,
        createdAt=h.created_at,
    )


@router.post("/heartbeat", response_model=DeviceHealthOut)
def send_heartbeat(payload: DeviceHeartbeat, db: Session = Depends(get_db)):
    """
    Gọi bởi chính thiết bị/Edge (KHÔNG yêu cầu auth admin) mỗi khi camera
    còn hoạt động. Cập nhật cả devices.status/last_seen_at song song.
    """
    device = db.query(Device).filter(Device.device_code == payload.deviceCode).first()
    if not device:
        raise HTTPException(status_code=404, detail="Mã thiết bị không hợp lệ")

    now = datetime.utcnow()

    health = DeviceHealth(
        device_id=device.id,
        status=payload.status,
        last_heartbeat_at=now,
        note=payload.note,
    )
    db.add(health)

    device.status = "online" if payload.status == "connected" else "offline"
    device.last_seen_at = now

    db.commit()
    db.refresh(health)
    return _to_out(health)


@router.get("", response_model=list[DeviceHealthOut])
def list_health(
    device_id: Optional[str] = None,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    query = db.query(DeviceHealth)
    if device_id:
        query = query.filter(DeviceHealth.device_id == device_id)
    records = query.order_by(DeviceHealth.created_at.desc()).limit(100).all()
    return [_to_out(h) for h in records]