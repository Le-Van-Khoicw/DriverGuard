from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import Optional

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.device_binding import DeviceBinding
from app.models.user import User
from app.models.device import Device
from app.schemas.device_binding import DeviceBindingCreate, DeviceBindingOut

router = APIRouter(prefix="/device-bindings", tags=["device-bindings"])


def _to_out(b: DeviceBinding) -> DeviceBindingOut:
    return DeviceBindingOut(
        id=b.id,
        userId=b.user_id,
        deviceId=b.device_id,
        status=b.status,
        boundAt=b.bound_at,
        unboundAt=b.unbound_at,
    )


@router.get("", response_model=list[DeviceBindingOut])
def list_bindings(
    user_id: Optional[str] = None,
    device_id: Optional[str] = None,
    status: Optional[str] = None,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    query = db.query(DeviceBinding)
    if user_id:
        query = query.filter(DeviceBinding.user_id == user_id)
    if device_id:
        query = query.filter(DeviceBinding.device_id == device_id)
    if status:
        query = query.filter(DeviceBinding.status == status)
    bindings = query.order_by(DeviceBinding.bound_at.desc()).all()
    return [_to_out(b) for b in bindings]


@router.post("", response_model=DeviceBindingOut)
def create_binding(
    payload: DeviceBindingCreate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    """Gán 1 camera cho 1 tài xế. Tự động hủy binding active cũ của camera đó (nếu có)."""
    user = db.query(User).filter(User.id == payload.userId).first()
    if not user:
        raise HTTPException(status_code=404, detail="Không tìm thấy tài khoản")

    device = db.query(Device).filter(Device.id == payload.deviceId).first()
    if not device:
        raise HTTPException(status_code=404, detail="Không tìm thấy thiết bị")

    # đóng binding active cũ của thiết bị này (nếu có) trước khi gán chủ mới
    old_binding = db.query(DeviceBinding).filter(
        DeviceBinding.device_id == payload.deviceId,
        DeviceBinding.status == "active",
    ).first()
    if old_binding:
        old_binding.status = "ended"
        old_binding.unbound_at = datetime.utcnow()

    new_binding = DeviceBinding(
        user_id=payload.userId,
        device_id=payload.deviceId,
        status="active",
        bound_at=datetime.utcnow(),
    )
    db.add(new_binding)
    db.commit()
    db.refresh(new_binding)
    return _to_out(new_binding)


@router.patch("/{binding_id}/unbind", response_model=DeviceBindingOut)
def unbind_device(
    binding_id: str,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    binding = db.query(DeviceBinding).filter(DeviceBinding.id == binding_id).first()
    if not binding:
        raise HTTPException(status_code=404, detail="Không tìm thấy bản ghi gán thiết bị")
    if binding.status == "ended":
        raise HTTPException(status_code=400, detail="Bản ghi này đã được hủy gán trước đó")

    binding.status = "ended"
    binding.unbound_at = datetime.utcnow()
    db.commit()
    db.refresh(binding)
    return _to_out(binding)