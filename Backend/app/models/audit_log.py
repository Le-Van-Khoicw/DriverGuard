import uuid
from sqlalchemy import Column, String, DateTime, JSON, func
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base

class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    admin_id = Column(CHAR(36), nullable=False)
    action = Column(String(100), nullable=False)
    target_table = Column(String(100), nullable=False)
    target_id = Column(CHAR(36), nullable=True)
    before_value = Column(JSON, nullable=True)
    after_value = Column(JSON, nullable=True)
    created_at = Column(DateTime, server_default=func.now())