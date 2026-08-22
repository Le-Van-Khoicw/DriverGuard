from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class DeviceCreate(BaseModel):
    deviceCode: str
    deviceName: str
    deviceType: str


class DeviceUpdate(BaseModel):
    deviceName: Optional[str] = None
    status: Optional[str] = None  # online | offline | locked


class DeviceOut(BaseModel):
    id: str
    deviceCode: str
    deviceName: str
    deviceType: str
    status: str
    firmwareVersion: Optional[str]
    aiModelVersion: Optional[str]
    lastSeenAt: Optional[datetime]
    createdAt: datetime
    updatedAt: datetime

    class Config:
        from_attributes = True