from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class DetectionSettingCreate(BaseModel):
    deviceId: Optional[str] = None  # null = cấu hình mặc định toàn hệ thống
    earThreshold: float
    confidenceThreshold: float
    closedDurationThresholdMs: int


class DetectionSettingUpdate(BaseModel):
    earThreshold: Optional[float] = None
    confidenceThreshold: Optional[float] = None
    closedDurationThresholdMs: Optional[int] = None


class DetectionSettingOut(BaseModel):
    id: str
    deviceId: Optional[str]
    earThreshold: float
    confidenceThreshold: float
    closedDurationThresholdMs: int
    updatedAt: datetime

    class Config:
        from_attributes = True