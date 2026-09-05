from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import Optional
from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.monitoring_session import MonitoringSession
from app.schemas.monitoring_session import (
    MonitoringSessionCreate, MonitoringSessionEnd, MonitoringSessionOut
)

router = APIRouter(prefix="/monitoring-sessions", tags=["monitoring-sessions"])


def _to_out(s: MonitoringSession) -> MonitoringSessionOut:
    return MonitoringSessionOut(
        id=s.id,
        userId=s.user_id,
        deviceId=s.device_id,
        vehicleId=s.vehicle_id,
        status=s.status,
        startedAt=s.started_at,
        endedAt=s.ended_at,
    )


@router.get("", response_model=list[MonitoringSessionOut])
def list_sessions(
    user_id: Optional[str] = None,
    device_id: Optional[str] = None,
    status: Optional[str] = None,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    query = db.query(MonitoringSession)
    if user_id:
        query = query.filter(MonitoringSession.user_id == user_id)
    if device_id:
        query = query.filter(MonitoringSession.device_id == device_id)
    if status:
        query = query.filter(MonitoringSession.status == status)
    sessions = query.order_by(MonitoringSession.started_at.desc()).all()
    return [_to_out(s) for s in sessions]


@router.get("/{session_id}", response_model=MonitoringSessionOut)
def get_session(
    session_id: str,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    session = db.query(MonitoringSession).filter(MonitoringSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Không tìm thấy session")
    return _to_out(session)


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
    return _to_out(session)


@router.patch("/{session_id}/end", response_model=MonitoringSessionOut)
def end_session(session_id: str, payload: MonitoringSessionEnd, db: Session = Depends(get_db)):
    session = db.query(MonitoringSession).filter(MonitoringSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Không tìm thấy session")
    session.status = "ended"
    session.ended_at = payload.endedAt
    db.commit()
    db.refresh(session)
    return _to_out(session)