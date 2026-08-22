import uuid
from sqlalchemy import Column, String, DateTime
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base

class DeviceBinding(Base):
    __tablename__ = "device_bindings"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(CHAR(36), nullable=False)
    device_id = Column(CHAR(36), nullable=False)
    status = Column(String(30), nullable=False)  # active | ended
    bound_at = Column(DateTime, nullable=False)
    unbound_at = Column(DateTime, nullable=True)