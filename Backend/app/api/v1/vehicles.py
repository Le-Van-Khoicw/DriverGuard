from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import Optional

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.vehicle import Vehicle
from app.models.user import User
from app.schemas.vehicle import VehicleCreate, VehicleUpdate, VehicleOut
from app.services.audit import log_action

router = APIRouter(prefix="/vehicles", tags=["vehicles"])


def _to_out(v: Vehicle) -> VehicleOut:
    return VehicleOut(
        id=v.id,
        userId=v.user_id,
        displayName=v.display_name,
        licensePlate=v.license_plate,
        vehicleType=v.vehicle_type,
        createdAt=v.created_at,
        updatedAt=v.updated_at,
    )


@router.get("", response_model=list[VehicleOut])
def list_vehicles(
    user_id: Optional[str] = None,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    query = db.query(Vehicle)
    if user_id:
        query = query.filter(Vehicle.user_id == user_id)
    vehicles = query.order_by(Vehicle.created_at.desc()).all()
    return [_to_out(v) for v in vehicles]


@router.get("/{vehicle_id}", response_model=VehicleOut)
def get_vehicle(
    vehicle_id: str,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    vehicle = db.query(Vehicle).filter(Vehicle.id == vehicle_id).first()
    if not vehicle:
        raise HTTPException(status_code=404, detail="Không tìm thấy phương tiện")
    return _to_out(vehicle)


@router.post("", response_model=VehicleOut)
def create_vehicle(
    payload: VehicleCreate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    user = db.query(User).filter(User.id == payload.userId).first()
    if not user:
        raise HTTPException(status_code=404, detail="Không tìm thấy tài khoản")

    vehicle = Vehicle(
        user_id=payload.userId,
        display_name=payload.displayName,
        license_plate=payload.licensePlate,
        vehicle_type=payload.vehicleType,
    )
    db.add(vehicle)
    db.commit()
    db.refresh(vehicle)

    log_action(
        db, admin_id=current_admin.id, action="create_vehicle",
        target_table="vehicles", target_id=vehicle.id,
        before_value=None,
        after_value={
            "displayName": vehicle.display_name,
            "licensePlate": vehicle.license_plate,
            "vehicleType": vehicle.vehicle_type,
        },
    )
    return _to_out(vehicle)


@router.patch("/{vehicle_id}", response_model=VehicleOut)
def update_vehicle(
    vehicle_id: str,
    payload: VehicleUpdate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    vehicle = db.query(Vehicle).filter(Vehicle.id == vehicle_id).first()
    if not vehicle:
        raise HTTPException(status_code=404, detail="Không tìm thấy phương tiện")

    before = {
        "displayName": vehicle.display_name,
        "licensePlate": vehicle.license_plate,
        "vehicleType": vehicle.vehicle_type,
    }

    if payload.displayName is not None:
        vehicle.display_name = payload.displayName
    if payload.licensePlate is not None:
        vehicle.license_plate = payload.licensePlate
    if payload.vehicleType is not None:
        vehicle.vehicle_type = payload.vehicleType

    db.commit()
    db.refresh(vehicle)

    log_action(
        db, admin_id=current_admin.id, action="update_vehicle",
        target_table="vehicles", target_id=vehicle.id,
        before_value=before,
        after_value={
            "displayName": vehicle.display_name,
            "licensePlate": vehicle.license_plate,
            "vehicleType": vehicle.vehicle_type,
        },
    )
    return _to_out(vehicle)


@router.delete("/{vehicle_id}", status_code=204)
def delete_vehicle(
    vehicle_id: str,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    vehicle = db.query(Vehicle).filter(Vehicle.id == vehicle_id).first()
    if not vehicle:
        raise HTTPException(status_code=404, detail="Không tìm thấy phương tiện")

    before = {
        "displayName": vehicle.display_name,
        "licensePlate": vehicle.license_plate,
        "vehicleType": vehicle.vehicle_type,
        "userId": vehicle.user_id,
    }
    vehicle_id_copy = vehicle.id  # lưu lại trước khi object bị xóa khỏi session

    db.delete(vehicle)
    db.commit()

    log_action(
        db, admin_id=current_admin.id, action="delete_vehicle",
        target_table="vehicles", target_id=vehicle_id_copy,
        before_value=before,
        after_value=None,
    )
    return None