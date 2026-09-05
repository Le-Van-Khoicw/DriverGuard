import uuid
from sqlalchemy import Column, String, Numeric, Integer, DateTime, Text, func
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base

class DrowsinessEvent(Base):
    __tablename__ = "drowsiness_events"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    session_id = Column(CHAR(36), nullable=False)
    event_type = Column(String(50), nullable=False)
    ear = Column(Numeric(6, 4), nullable=True)
    confidence = Column(Numeric(6, 4), nullable=True)
    closed_duration_ms = Column(Integer, nullable=True)
    image_url = Column(String(500), nullable=True)
    source = Column(String(50), nullable=False)  # ANDROID | EDGE_DEVICE
    occurred_at = Column(DateTime, nullable=False)
    status = Column(String(30), nullable=False, default="NEW")  # NEW | ACKNOWLEDGED | RESOLVED
    handled_by = Column(CHAR(36), nullable=True)
    note = Column(Text, nullable=True)
    latitude = Column(Numeric(10, 7), nullable=True)
    longitude = Column(Numeric(10, 7), nullable=True)
    created_at = Column(DateTime, server_default=func.now())