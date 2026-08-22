from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.db.session import get_db
from app.models.monitoring_session import MonitoringSession
from app.schemas.monitoring_session import (
    MonitoringSessionCreate, MonitoringSessionEnd, MonitoringSessionOut
)

router = APIRouter(prefix="/monitoring-sessions", tags=["monitoring-sessions"])

@router.post("", response_model=MonitoringSessionOut)
def start_session(payload: MonitoringSessionCreate, db: Session = Depends(get_db)):
    session = MonitoringSession(
        user_id=payload.userId,
        device_id=payload.deviceId,
        vehicle_id=payload.vehicleId,
        status="active",
        started_at=payload.startedAt,
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    return MonitoringSessionOut(
        id=session.id, status=session.status,
        startedAt=session.started_at, endedAt=session.ended_at
    )

@router.patch("/{session_id}/end", response_model=MonitoringSessionOut)
def end_session(session_id: str, payload: MonitoringSessionEnd, db: Session = Depends(get_db)):
    session = db.query(MonitoringSession).filter(MonitoringSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Không tìm thấy session")
    session.status = "ended"
    session.ended_at = payload.endedAt
    db.commit()
    db.refresh(session)
    return MonitoringSessionOut(
        id=session.id, status=session.status,
        startedAt=session.started_at, endedAt=session.ended_at
    )