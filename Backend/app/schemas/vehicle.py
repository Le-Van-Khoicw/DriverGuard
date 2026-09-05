from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class VehicleCreate(BaseModel):
    userId: str
    displayName: str
    licensePlate: Optional[str] = None
    vehicleType: Optional[str] = None  # motorbike | car | truck | bus


class VehicleUpdate(BaseModel):
    displayName: Optional[str] = None
    licensePlate: Optional[str] = None
    vehicleType: Optional[str] = None


class VehicleOut(BaseModel):
    id: str
    userId: str
    displayName: str
    licensePlate: Optional[str]
    vehicleType: Optional[str]
    createdAt: datetime
    updatedAt: datetime

    class Config:
        from_attributes = True