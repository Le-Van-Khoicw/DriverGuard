import uuid
from sqlalchemy import Column, String, DateTime, func
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base

class DeviceHealth(Base):
    __tablename__ = "device_health"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = Column(CHAR(36), nullable=False)
    status = Column(String(30), nullable=False)  # connected | disconnected | warning
    last_heartbeat_at = Column(DateTime, nullable=True)
    note = Column(String(255), nullable=True)
    created_at = Column(DateTime, server_default=func.now())