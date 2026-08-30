from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.user import User
from app.models.device import Device
from app.models.vehicle import Vehicle
from app.schemas.global_search import SearchResultItem

router = APIRouter(prefix="/search", tags=["global-search"])


@router.get("", response_model=list[SearchResultItem])
def search(
    q: str = Query(..., min_length=1, description="Từ khóa: tên, SĐT, mã thiết bị, biển số..."),
    limit: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    keyword = f"%{q.strip()}%"
    results: list[SearchResultItem] = []

    # --- users: tìm theo họ tên, số điện thoại, username ---
    users = (
        db.query(User)
        .filter(
            (User.full_name.ilike(keyword))
            | (User.phone.ilike(keyword))
            | (User.username.ilike(keyword))
        )
        .limit(limit)
        .all()
    )
    for u in users:
        results.append(
            SearchResultItem(
                type="user",
                id=u.id,
                title=u.full_name,
                subtitle=u.phone or u.username or u.role,
            )
        )

    # --- devices: tìm theo mã thiết bị, tên thiết bị ---
    devices = (
        db.query(Device)
        .filter(
            (Device.device_code.ilike(keyword))
            | (Device.device_name.ilike(keyword))
        )
        .limit(limit)
        .all()
    )
    for d in devices:
        results.append(
            SearchResultItem(
                type="device",
                id=d.id,
                title=d.device_code,
                subtitle=d.device_name,
            )
        )

    # --- vehicles: tìm theo biển số, tên xe ---
    vehicles = (
        db.query(Vehicle)
        .filter(
            (Vehicle.license_plate.ilike(keyword))
            | (Vehicle.display_name.ilike(keyword))
        )
        .limit(limit)
        .all()
    )
    for v in vehicles:
        results.append(
            SearchResultItem(
                type="vehicle",
                id=v.id,
                title=v.license_plate or v.display_name,
                subtitle=v.display_name,
            )
        )

    return results[:limit]