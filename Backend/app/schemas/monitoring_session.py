from datetime import datetime
from typing import Optional
from pydantic import BaseModel

class MonitoringSessionCreate(BaseModel):
    userId: str
    deviceId: str
    vehicleId: Optional[str] = None
    startedAt: datetime

class MonitoringSessionEnd(BaseModel):
    endedAt: datetime

class MonitoringSessionOut(BaseModel):
    id: str
    status: str
    startedAt: datetime
    endedAt: Optional[datetime] = None

    class Config:
        from_attributes = True