import csv
import io
from datetime import datetime
from typing import Optional
from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.drowsiness_event import DrowsinessEvent
from app.models.monitoring_session import MonitoringSession

router = APIRouter(prefix="/reports", tags=["reports"])


@router.get("/export")
def export_drowsiness_events(
    from_date: Optional[datetime] = Query(None, alias="fromDate"),
    to_date: Optional[datetime] = Query(None, alias="toDate"),
    device_id: Optional[str] = Query(None, alias="deviceId"),
    user_id: Optional[str] = Query(None, alias="userId"),
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    """Xuất CSV lịch sử cảnh báo theo điều kiện lọc. Mở được bằng Excel."""
    query = db.query(DrowsinessEvent).join(
        MonitoringSession, DrowsinessEvent.session_id == MonitoringSession.id
    )

    if from_date:
        query = query.filter(DrowsinessEvent.occurred_at >= from_date)
    if to_date:
        query = query.filter(DrowsinessEvent.occurred_at <= to_date)
    if device_id:
        query = query.filter(MonitoringSession.device_id == device_id)
    if user_id:
        query = query.filter(MonitoringSession.user_id == user_id)

    events = query.order_by(DrowsinessEvent.occurred_at.desc()).all()

    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow([
        "id", "session_id", "event_type", "ear", "confidence",
        "closed_duration_ms", "status", "handled_by", "note", "occurred_at",
    ])
    for e in events:
        writer.writerow([
            e.id, e.session_id, e.event_type, e.ear, e.confidence,
            e.closed_duration_ms, e.status, e.handled_by, e.note, e.occurred_at,
        ])

    buffer.seek(0)
    filename = f"drowsiness_events_{datetime.utcnow().strftime('%Y%m%d_%H%M%S')}.csv"

    return StreamingResponse(
        iter([buffer.getvalue()]),
        media_type="text/csv",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )