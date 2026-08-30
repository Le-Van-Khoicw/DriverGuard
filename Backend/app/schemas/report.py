from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class ReportFilter(BaseModel):
    fromDate: Optional[datetime] = None
    toDate: Optional[datetime] = None
    deviceId: Optional[str] = None
    userId: Optional[str] = None