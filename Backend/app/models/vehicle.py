import uuid
from sqlalchemy import Column, String, DateTime, func
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base

class Vehicle(Base):
    __tablename__ = "vehicles"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(CHAR(36), nullable=False)
    display_name = Column(String(150), nullable=False)
    license_plate = Column(String(30), nullable=True)
    vehicle_type = Column(String(50), nullable=True)  # motorbike | car | truck | bus
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())