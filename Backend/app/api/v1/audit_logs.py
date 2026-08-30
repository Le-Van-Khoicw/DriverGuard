from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from typing import Optional

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.audit_log import AuditLog
from app.schemas.audit_log import AuditLogOut

router = APIRouter(prefix="/audit-logs", tags=["audit-logs"])


def _to_out(a: AuditLog) -> AuditLogOut:
    return AuditLogOut(
        id=a.id, adminId=a.admin_id, action=a.action,
        targetTable=a.target_table, targetId=a.target_id,
        beforeValue=a.before_value, afterValue=a.after_value,
        createdAt=a.created_at,
    )


@router.get("", response_model=list[AuditLogOut])
def list_audit_logs(
    admin_id: Optional[str] = None,
    target_table: Optional[str] = None,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    query = db.query(AuditLog)
    if admin_id:
        query = query.filter(AuditLog.admin_id == admin_id)
    if target_table:
        query = query.filter(AuditLog.target_table == target_table)

    logs = (
        query.order_by(AuditLog.created_at.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
        .all()
    )
    return [_to_out(a) for a in logs]