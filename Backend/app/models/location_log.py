import uuid
from sqlalchemy import Column, String, Numeric, DateTime
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base


class LocationLog(Base):
    __tablename__ = "location_logs"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    session_id = Column(CHAR(36), nullable=False)
    device_id = Column(CHAR(36), nullable=False)
    latitude = Column(Numeric(10, 7), nullable=False)
    longitude = Column(Numeric(10, 7), nullable=False)
    speed_kmh = Column(Numeric(6, 2), nullable=True)
    recorded_at = Column(DateTime, nullable=False)