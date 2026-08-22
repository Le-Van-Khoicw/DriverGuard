from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from app.db.session import get_db
from app.models.user import User
from app.core.security import verify_password, create_access_token

router = APIRouter(prefix="/auth", tags=["auth"])

@router.post("/login")
def login(form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == form_data.username, User.role == "admin").first()
    if not user or not verify_password(form_data.password, user.password_hash):
        raise HTTPException(status_code=400, detail="Sai tài khoản hoặc mật khẩu")
    token = create_access_token(subject=user.id)
    return {"access_token": token, "token_type": "bearer"}