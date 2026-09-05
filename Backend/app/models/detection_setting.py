import uuid
from sqlalchemy import Column, Numeric, Integer, DateTime, func
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base

class DetectionSetting(Base):
    __tablename__ = "detection_settings"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = Column(CHAR(36), nullable=True)  # null = cấu hình mặc định toàn hệ thống
    ear_threshold = Column(Numeric(6, 4), nullable=False)
    confidence_threshold = Column(Numeric(6, 4), nullable=False)
    closed_duration_threshold_ms = Column(Integer, nullable=False)
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())