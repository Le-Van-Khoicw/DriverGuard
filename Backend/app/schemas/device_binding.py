from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class DeviceBindingCreate(BaseModel):
    userId: str
    deviceId: str


class DeviceBindingOut(BaseModel):
    id: str
    userId: str
    deviceId: str
    status: str  # active | ended
    boundAt: datetime
    unboundAt: Optional[datetime]

    class Config:
        from_attributes = True