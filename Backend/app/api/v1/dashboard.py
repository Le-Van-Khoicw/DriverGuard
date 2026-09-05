from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, Query
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.device import Device
from app.models.monitoring_session import MonitoringSession
from app.models.drowsiness_event import DrowsinessEvent
from app.schemas.dashboard import DashboardSummary, AlertTrendPoint

router = APIRouter(prefix="/dashboard", tags=["dashboard"])


@router.get("/summary", response_model=DashboardSummary)
def get_summary(
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)

    total_devices = db.query(func.count(Device.id)).scalar()
    online_devices = db.query(func.count(Device.id)).filter(Device.status == "online").scalar()
    offline_devices = db.query(func.count(Device.id)).filter(Device.status != "online").scalar()

    sessions_today = db.query(func.count(MonitoringSession.id)).filter(
        MonitoringSession.started_at >= today_start
    ).scalar()

    alerts_today = db.query(func.count(DrowsinessEvent.id)).filter(
        DrowsinessEvent.occurred_at >= today_start
    ).scalar()

    unhandled_alerts = db.query(func.count(DrowsinessEvent.id)).filter(
        DrowsinessEvent.status == "NEW"
    ).scalar()

    return DashboardSummary(
        totalDevices=total_devices,
        onlineDevices=online_devices,
        offlineDevices=offline_devices,
        sessionsToday=sessions_today,
        alertsToday=alerts_today,
        unhandledAlerts=unhandled_alerts,
    )


@router.get("/alert-trend", response_model=list[AlertTrendPoint])
def get_alert_trend(
    days: int = Query(7, ge=1, le=90, description="Số ngày gần nhất muốn xem"),
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):

    since = datetime.utcnow() - timedelta(days=days)

    rows = (
        db.query(
            func.date(DrowsinessEvent.occurred_at).label("date"),
            func.count(DrowsinessEvent.id).label("count"),
        )
        .filter(DrowsinessEvent.occurred_at >= since)
        .group_by(func.date(DrowsinessEvent.occurred_at))
        .order_by(func.date(DrowsinessEvent.occurred_at))
        .all()
    )

    return [AlertTrendPoint(date=str(r.date), count=r.count) for r in rows]


@router.get("/recent-alerts")
def get_recent_alerts(
    limit: int = Query(5, ge=1, le=50),
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    events = (
        db.query(DrowsinessEvent)
        .order_by(DrowsinessEvent.occurred_at.desc())
        .limit(limit)
        .all()
    )
    return [
        {
            "id": e.id,
            "sessionId": e.session_id,
            "eventType": e.event_type,
            "occurredAt": e.occurred_at,
            "status": e.status,
        }
        for e in events
    ]