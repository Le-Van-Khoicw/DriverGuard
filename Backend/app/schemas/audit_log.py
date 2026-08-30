from datetime import datetime
from typing import Optional, Any
from pydantic import BaseModel


class AuditLogOut(BaseModel):
    id: str
    adminId: str
    action: str
    targetTable: str
    targetId: Optional[str]
    beforeValue: Optional[dict[str, Any]]
    afterValue: Optional[dict[str, Any]]
    createdAt: datetime

    class Config:
        from_attributes = True