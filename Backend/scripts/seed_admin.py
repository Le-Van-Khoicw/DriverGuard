"""
Seed tài khoản admin đầu tiên cho hệ thống.
Chạy sau khi đã migrate xong: python scripts/seed_admin.py

Tùy chỉnh qua biến môi trường (đọc từ .env nếu có):
- SEED_ADMIN_USERNAME (mặc định: admin)
- SEED_ADMIN_PASSWORD (mặc định: admin123 - cần đổi khi đăng nhập lần đầu)
- SEED_ADMIN_FULL_NAME (mặc định: Quản trị viên)
"""
import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app.db.session import SessionLocal
from app.models.user import User
from app.core.security import hash_password


def seed_admin():
    username = os.getenv("SEED_ADMIN_USERNAME", "admin")
    password = os.getenv("SEED_ADMIN_PASSWORD", "admin123")
    full_name = os.getenv("SEED_ADMIN_FULL_NAME", "Quản trị viên")

    db = SessionLocal()
    try:
        existing = db.query(User).filter(User.username == username).first()
        if existing:
            print(f"Tài khoản '{username}' đã tồn tại, bỏ qua.")
            return

        admin = User(
            username=username,
            password_hash=hash_password(password),
            full_name=full_name,
            role="admin",
            is_active=True,
        )
        db.add(admin)
        db.commit()
        print(f"Đã tạo tài khoản admin: username='{username}'")
        if password == "admin123":
            print("CẢNH BÁO: đang dùng mật khẩu mặc định, hãy đổi ngay sau khi đăng nhập lần đầu.")
    finally:
        db.close()


if __name__ == "__main__":
    seed_admin()