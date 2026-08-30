from typing import Optional, Any
from sqlalchemy.orm import Session
from app.models.audit_log import AuditLog


def log_action(
    db: Session,
    admin_id: str,
    action: str,
    target_table: str,
    target_id: Optional[str] = None,
    before_value: Optional[dict[str, Any]] = None,
    after_value: Optional[dict[str, Any]] = None,
):
    """Ghi 1 dòng audit log. Gọi ngay sau khi mutation thành công, TRƯỚC db.commit() cuối cùng
    nếu muốn cùng transaction, hoặc sau vì đây là thao tác độc lập."""
    entry = AuditLog(
        admin_id=admin_id,
        action=action,
        target_table=target_table,
        target_id=target_id,
        before_value=before_value,
        after_value=after_value,
    )
    db.add(entry)
    db.commit()