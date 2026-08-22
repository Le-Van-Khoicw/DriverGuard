import uuid
from sqlalchemy import Column, String, Enum, DateTime, func
from sqlalchemy.dialects.mysql import CHAR
from app.db.base_class import Base

class User(Base):
    __tablename__ = "users"

    id = Column(CHAR(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    full_name = Column(String(255), nullable=False)
    phone = Column(String(20), nullable=True)
    email = Column(String(255), unique=True, nullable=True)
    password_hash = Column(String(255), nullable=True)  # null nếu là driver
    role = Column(Enum("admin", "driver", name="user_role"), nullable=False)
    status = Column(Enum("active", "locked", name="user_status"), default="active")
    created_at = Column(DateTime, server_default=func.now())