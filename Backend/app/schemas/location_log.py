from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class LocationLogCreate(BaseModel):
    sessionId: str
    deviceId: str
    latitude: float
    longitude: float
    speedKmh: Optional[float] = None
    recordedAt: datetime


class LocationLogOut(BaseModel):
    id: str
    sessionId: str
    deviceId: str
    latitude: float
    longitude: float
    speedKmh: Optional[float]
    recordedAt: datetime

    class Config:
        from_attributes = True


class LatestLocationOut(BaseModel):
    deviceId: str
    sessionId: str
    latitude: float
    longitude: float
    speedKmh: Optional[float]
    recordedAt: datetime