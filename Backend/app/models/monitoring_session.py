import uuid
from sqlalchemy import Column, String, DateTime, func
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base

class MonitoringSession(Base):
    __tablename__ = "monitoring_sessions"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(CHAR(36), nullable=False)
    device_id = Column(CHAR(36), nullable=False)
    vehicle_id = Column(CHAR(36), nullable=True)
    status = Column(String(30), nullable=False, default="active")  # active | ended
    started_at = Column(DateTime, nullable=False)
    ended_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, server_default=func.now())