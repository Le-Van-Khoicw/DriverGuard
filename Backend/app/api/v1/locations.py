from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func
from sqlalchemy.orm import Session
from typing import Optional

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.location_log import LocationLog
from app.models.monitoring_session import MonitoringSession
from app.schemas.location_log import LocationLogCreate, LocationLogOut, LatestLocationOut

router = APIRouter(prefix="/locations", tags=["locations"])


def _to_out(loc: LocationLog) -> LocationLogOut:
    return LocationLogOut(
        id=loc.id,
        sessionId=loc.session_id,
        deviceId=loc.device_id,
        latitude=float(loc.latitude),
        longitude=float(loc.longitude),
        speedKmh=float(loc.speed_kmh) if loc.speed_kmh is not None else None,
        recordedAt=loc.recorded_at,
    )


@router.post("", response_model=LocationLogOut)
def create_location(payload: LocationLogCreate, db: Session = Depends(get_db)):

    session = db.query(MonitoringSession).filter(
        MonitoringSession.id == payload.sessionId
    ).first()
    if not session:
        raise HTTPException(status_code=400, detail="sessionId không hợp lệ")

    location = LocationLog(
        session_id=payload.sessionId,
        device_id=payload.deviceId,
        latitude=payload.latitude,
        longitude=payload.longitude,
        speed_kmh=payload.speedKmh,
        recorded_at=payload.recordedAt,
    )
    db.add(location)
    db.commit()
    db.refresh(location)
    return _to_out(location)


@router.get("", response_model=list[LocationLogOut])
def list_locations(
    session_id: str = Query(..., description="Xem lịch trình di chuyển của 1 phiên cụ thể"),
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    locations = (
        db.query(LocationLog)
        .filter(LocationLog.session_id == session_id)
        .order_by(LocationLog.recorded_at.asc())
        .all()
    )
    return [_to_out(loc) for loc in locations]


@router.get("/latest", response_model=list[LatestLocationOut])
def get_latest_locations(
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    
    active_sessions = db.query(MonitoringSession).filter(
        MonitoringSession.status == "active"
    ).all()

    results: list[LatestLocationOut] = []
    for s in active_sessions:
        latest = (
            db.query(LocationLog)
            .filter(LocationLog.session_id == s.id)
            .order_by(LocationLog.recorded_at.desc())
            .first()
        )
        if latest:
            results.append(
                LatestLocationOut(
                    deviceId=latest.device_id,
                    sessionId=latest.session_id,
                    latitude=float(latest.latitude),
                    longitude=float(latest.longitude),
                    speedKmh=float(latest.speed_kmh) if latest.speed_kmh is not None else None,
                    recordedAt=latest.recorded_at,
                )
            )
    return results