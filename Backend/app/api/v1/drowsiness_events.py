from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.drowsiness_event import DrowsinessEvent
from app.models.monitoring_session import MonitoringSession
from app.schemas.drowsiness_event import (
    DrowsinessEventCreate, DrowsinessEventStatusUpdate, DrowsinessEventOut
)

router = APIRouter(prefix="/drowsiness-events", tags=["drowsiness-events"])

def _to_out(e: DrowsinessEvent) -> DrowsinessEventOut:
    return DrowsinessEventOut(
        id=e.id, sessionId=e.session_id, eventType=e.event_type,
        ear=float(e.ear) if e.ear is not None else None,
        confidence=float(e.confidence) if e.confidence is not None else None,
        closedDurationMs=e.closed_duration_ms, imageUrl=e.image_url,
        occurredAt=e.occurred_at, status=e.status,
        handledBy=e.handled_by, note=e.note,
    )

@router.post("", response_model=DrowsinessEventOut)
def create_event(payload: DrowsinessEventCreate, db: Session = Depends(get_db)):
    session = db.query(MonitoringSession).filter(
        MonitoringSession.id == payload.sessionId
    ).first()
    if not session:
        raise HTTPException(status_code=400, detail="sessionId không hợp lệ, phải mở session trước")

    event = DrowsinessEvent(
        session_id=payload.sessionId,
        event_type=payload.eventType,
        ear=payload.ear,
        confidence=payload.confidence,
        closed_duration_ms=payload.closedDurationMs,
        image_url=payload.imageUrl,
        source=payload.source,
        occurred_at=payload.occurredAt,
        status="NEW",
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return _to_out(event)

@router.get("")
def list_events(
    deviceId: str | None = None,
    userId: str | None = None,
    status: str | None = None,
    page: int = 1,
    pageSize: int = 20,
    db: Session = Depends(get_db),
):
    query = db.query(DrowsinessEvent).join(
        MonitoringSession, DrowsinessEvent.session_id == MonitoringSession.id
    )
    if deviceId:
        query = query.filter(MonitoringSession.device_id == deviceId)
    if userId:
        query = query.filter(MonitoringSession.user_id == userId)
    if status:
        query = query.filter(DrowsinessEvent.status == status)

    total = query.count()
    items = query.order_by(DrowsinessEvent.occurred_at.desc()) \
                  .offset((page - 1) * pageSize).limit(pageSize).all()

    return {
        "items": [_to_out(e) for e in items],
        "total": total,
        "page": page,
        "pageSize": pageSize,
    }

@router.patch("/{event_id}/status", response_model=DrowsinessEventOut)
def update_status(
    event_id: str,
    payload: DrowsinessEventStatusUpdate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),  # chỉ admin mới xử lý được
):
    event = db.query(DrowsinessEvent).filter(DrowsinessEvent.id == event_id).first()
    if not event:
        raise HTTPException(status_code=404, detail="Không tìm thấy cảnh báo")

    event.status = payload.status
    event.note = payload.note
    event.handled_by = current_admin.id  # lấy từ token, không nhận từ client
    db.commit()
    db.refresh(event)
    return _to_out(event)