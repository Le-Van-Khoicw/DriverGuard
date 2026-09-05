# DriverGuard Backend

Backend hệ thống quản lý phát hiện buồn ngủ tài xế qua camera — vai trò Admin quản lý thiết bị camera và dữ liệu giám sát/cảnh báo do tài xế (qua Mobile) và Edge gửi lên.

## Tech stack

- Python FastAPI
- MySQL + SQLAlchemy (ORM) + Alembic (migration)
- JWT (python-jose) + bcrypt

## Setup

```bash
python -m venv venv
source venv/bin/activate      # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

Copy `.env.example` thành `.env`, điền `DATABASE_URL` và `JWT_SECRET_KEY` thật.

## Migration

```bash
alembic upgrade head
```

## Seed dữ liệu ban đầu

```bash
python scripts/seed_admin.py
```
Tạo tài khoản admin đầu tiên (mặc định `admin`/`admin123`, đổi qua `.env` nếu cần — xem comment đầu file script).

## Chạy server

```bash
uvicorn app.main:app --reload
```
Swagger UI: `http://127.0.0.1:8000/docs`

## Tiến độ hiện tại

| Module | Trạng thái |
|---|---|
| Auth (JWT, username + bcrypt) | Hoàn thành |
| Users | Hoàn thành |
| Devices | Hoàn thành |
| Device Bindings | Hoàn thành |
| Vehicles | Hoàn thành |
| Monitoring Sessions | Hoàn thành |
| Drowsiness Events (kèm alert workflow) | Hoàn thành |
| Detection Settings | Hoàn thành |
| Device Health | Hoàn thành |
| Dashboard | Hoàn thành |
| Global Search | Hoàn thành |
| Reports (export CSV) | Hoàn thành |
| Audit Logs (tích hợp toàn bộ endpoint mutation) | Hoàn thành |
| CORS cho Web Admin | Đã bật |
| Device authentication cho endpoint Edge | Chưa làm — cần trước khi lên production |

## Tài liệu liên quan

- Cấu trúc dữ liệu: `../docs/erd.dbml`
- API Contract (nguồn chân lý cho Mobile/Web/Edge): `../docs/api-contract.md`