# app/models/device.py
import uuid
from sqlalchemy import Column, String, DateTime, func
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base

class Device(Base):
    __tablename__ = "devices"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_code = Column(String(100), unique=True, nullable=False)
    device_name = Column(String(150), nullable=False)
    device_type = Column(String(50), nullable=False)
    status = Column(String(30), nullable=False)  # online | offline | locked
    firmware_version = Column(String(50), nullable=True)
    ai_model_version = Column(String(50), nullable=True)
    last_seen_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())