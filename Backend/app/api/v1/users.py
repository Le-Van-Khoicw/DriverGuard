from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import Optional

from app.db.session import get_db
from app.api.deps import get_current_admin
from app.models.user import User
from app.core.security import hash_password
from app.schemas.user import UserCreate, UserUpdate, UserOut
from app.services.audit import log_action

router = APIRouter(prefix="/users", tags=["users"])


@router.get("", response_model=list[UserOut])
def list_users(
    role: Optional[str] = Query(None, description="Lọc theo role: admin | driver"),
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    query = db.query(User)
    if role:
        query = query.filter(User.role == role)
    return query.order_by(User.created_at.desc()).all()


@router.get("/{user_id}", response_model=UserOut)
def get_user(
    user_id: str,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="Không tìm thấy tài khoản")
    return user


@router.post("", response_model=UserOut)
def create_user(
    payload: UserCreate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    if payload.role == "admin" and not payload.password:
        raise HTTPException(status_code=400, detail="Tài khoản admin bắt buộc phải có password")

    if payload.username:
        existing = db.query(User).filter(User.username == payload.username).first()
        if existing:
            raise HTTPException(status_code=400, detail="Username đã tồn tại")

    if payload.phone:
        existing = db.query(User).filter(User.phone == payload.phone).first()
        if existing:
            raise HTTPException(status_code=400, detail="Số điện thoại đã được sử dụng")

    user = User(
        full_name=payload.full_name,
        phone=payload.phone,
        username=payload.username,
        password_hash=hash_password(payload.password) if payload.password else None,
        role=payload.role,
        is_active=payload.is_active,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.patch("/{user_id}", response_model=UserOut)
def update_user(
    user_id: str,
    payload: UserUpdate,
    db: Session = Depends(get_db),
    current_admin=Depends(get_current_admin),
):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="Không tìm thấy tài khoản")
    
    before = {
        "full_name": user.full_name,
        "phone": user.phone,
        "is_active": user.is_active,
    }

    if payload.full_name is not None:
        user.full_name = payload.full_name
    if payload.phone is not None:
        user.phone = payload.phone
    if payload.is_active is not None:
        user.is_active = payload.is_active  # dùng để khóa/mở khóa tài khoản

    db.commit()
    db.refresh(user)

    log_action(
        db, admin_id=current_admin.id, action="update_user",
        target_table="users", target_id=user.id,
        before_value=before,
        after_value={
            "full_name": user.full_name,
            "phone": user.phone,
            "is_active": user.is_active,
        },
    )
    return user