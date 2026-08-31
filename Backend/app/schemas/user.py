from datetime import datetime
from typing import Optional, Literal
from pydantic import BaseModel


class UserBase(BaseModel):
    full_name: str
    phone: Optional[str] = None
    username: Optional[str] = None
    role: Literal["admin", "driver"]
    is_active: bool = True


class UserCreate(UserBase):
    password: Optional[str] = None


class UserUpdate(BaseModel):
    full_name: Optional[str] = None
    phone: Optional[str] = None
    is_active: Optional[bool] = None


class UserOut(BaseModel):
    id: str
    username: Optional[str]
    phone: Optional[str]
    fullName: str
    role: str
    isActive: bool
    createdAt: datetime
    updatedAt: datetime

    class Config:
        from_attributes = True