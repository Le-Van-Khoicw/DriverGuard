from datetime import datetime
from typing import Optional
from pydantic import BaseModel

class DrowsinessEventCreate(BaseModel):
    sessionId: str
    eventType: str
    ear: Optional[float] = None
    confidence: Optional[float] = None
    closedDurationMs: Optional[int] = None
    imageUrl: Optional[str] = None
    source: str
    occurredAt: datetime

class DrowsinessEventStatusUpdate(BaseModel):
    status: str  # NEW | ACKNOWLEDGED | RESOLVED
    note: Optional[str] = None

class DrowsinessEventOut(BaseModel):
    id: str
    sessionId: str
    eventType: str
    ear: Optional[float]
    confidence: Optional[float]
    closedDurationMs: Optional[int]
    imageUrl: Optional[str]
    occurredAt: datetime
    status: str
    handledBy: Optional[str]
    note: Optional[str]

    class Config:
        from_attributes = True