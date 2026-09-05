from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class DeviceHeartbeat(BaseModel):
    deviceCode: str
    status: Optional[str] = "connected"  # connected | warning
    note: Optional[str] = None


class DeviceHealthOut(BaseModel):
    id: str
    deviceId: str
    status: str
    lastHeartbeatAt: Optional[datetime]
    note: Optional[str]
    createdAt: datetime

    class Config:
        from_attributes = True