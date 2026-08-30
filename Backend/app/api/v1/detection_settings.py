from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import Optional

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.detection_setting import DetectionSetting
from app.models.device import Device
from app.schemas.detection_setting import (
    DetectionSettingCreate, DetectionSettingUpdate, DetectionSettingOut
)

router = APIRouter(prefix="/detection-settings", tags=["detection-settings"])


def _to_out(s: DetectionSetting) -> DetectionSettingOut:
    return DetectionSettingOut(
        id=s.id,
        deviceId=s.device_id,
        earThreshold=float(s.ear_threshold),
        confidenceThreshold=float(s.confidence_threshold),
        closedDurationThresholdMs=s.closed_duration_threshold_ms,
        updatedAt=s.updated_at,
    )


@router.get("", response_model=list[DetectionSettingOut])
def list_settings(
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    settings = db.query(DetectionSetting).all()
    return [_to_out(s) for s in settings]


@router.get("/effective", response_model=DetectionSettingOut)
def get_effective_settings(
    device_id: Optional[str] = None,
    db: Session = Depends(get_db),
):

    setting = None
    if device_id:
        setting = db.query(DetectionSetting).filter(
            DetectionSetting.device_id == device_id
        ).first()

    if not setting:
        setting = db.query(DetectionSetting).filter(
            DetectionSetting.device_id.is_(None)
        ).first()

    if not setting:
        raise HTTPException(status_code=404, detail="Chưa có cấu hình ngưỡng mặc định nào được tạo")

    return _to_out(setting)


@router.post("", response_model=DetectionSettingOut)
def create_or_update_settings(
    payload: DetectionSettingCreate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):

    if payload.deviceId:
        device = db.query(Device).filter(Device.id == payload.deviceId).first()
        if not device:
            raise HTTPException(status_code=404, detail="Không tìm thấy thiết bị")

    query = db.query(DetectionSetting)
    query = query.filter(DetectionSetting.device_id == payload.deviceId) if payload.deviceId \
        else query.filter(DetectionSetting.device_id.is_(None))
    existing = query.first()

    if existing:
        existing.ear_threshold = payload.earThreshold
        existing.confidence_threshold = payload.confidenceThreshold
        existing.closed_duration_threshold_ms = payload.closedDurationThresholdMs
        db.commit()
        db.refresh(existing)
        return _to_out(existing)

    setting = DetectionSetting(
        device_id=payload.deviceId,
        ear_threshold=payload.earThreshold,
        confidence_threshold=payload.confidenceThreshold,
        closed_duration_threshold_ms=payload.closedDurationThresholdMs,
    )
    db.add(setting)
    db.commit()
    db.refresh(setting)
    return _to_out(setting)


@router.patch("/{setting_id}", response_model=DetectionSettingOut)
def update_settings(
    setting_id: str,
    payload: DetectionSettingUpdate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    setting = db.query(DetectionSetting).filter(DetectionSetting.id == setting_id).first()
    if not setting:
        raise HTTPException(status_code=404, detail="Không tìm thấy cấu hình")

    if payload.earThreshold is not None:
        setting.ear_threshold = payload.earThreshold
    if payload.confidenceThreshold is not None:
        setting.confidence_threshold = payload.confidenceThreshold
    if payload.closedDurationThresholdMs is not None:
        setting.closed_duration_threshold_ms = payload.closedDurationThresholdMs

    db.commit()
    db.refresh(setting)
    return _to_out(setting)