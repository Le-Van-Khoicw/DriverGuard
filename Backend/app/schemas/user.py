from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class UserBase(BaseModel):
    full_name: str
    phone: Optional[str] = None
    username: Optional[str] = None  # null với driver
    role: str  # admin | driver
    is_active: bool = True


class UserCreate(UserBase):
    password: Optional[str] = None  # bắt buộc nếu role = admin, bỏ trống nếu role = driver


class UserUpdate(BaseModel):
    full_name: Optional[str] = None
    phone: Optional[str] = None
    is_active: Optional[bool] = None


class UserOut(BaseModel):
    id: str
    username: Optional[str]
    phone: Optional[str]
    full_name: str
    role: str
    is_active: bool
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True