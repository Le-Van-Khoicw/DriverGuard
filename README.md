# DriverGuard

Hệ thống phát hiện và cảnh báo buồn ngủ bằng camera, gồm ứng dụng Android, Backend API, Web Dashboard và thiết bị Edge trong giai đoạn mở rộng.

## Cấu trúc

- `Mobile/DriverGuard`: Android app Kotlin + Jetpack Compose.
- `Backend`: Python FastAPI và MySQL.
- `Web`: Web Dashboard cho nhân viên vận hành.
- `Edge`: mã cho Raspberry Pi/ESP32 ở giai đoạn phần cứng.
- `docs`: ERD và hợp đồng API dùng chung.

## Trạng thái hiện tại

Mobile đang có màn hình giám sát giả lập theo MVVM. CameraX, AI, Backend và Web sẽ được tích hợp từng bước.

## Hướng dẫn cài đặt môi trường Backend

### Yêu cầu

- Python 3.11+ (dự án đang test trên Python 3.13)
- MySQL Server đã cài đặt và đang chạy
- Git

### Các bước cài đặt

1. **Clone/pull source code**

```bash
   git clone <repo-url>
   cd DriverGuard/Backend
```

2. **Tạo virtual environment** (chỉ cần làm 1 lần)

```bash
   python -m venv venv
```

3. **Kích hoạt virtual environment**

   - Windows (PowerShell):
```powershell
     venv\Scripts\activate
```
   - macOS/Linux:
```bash
     source venv/bin/activate
```

4. **Cài đặt các thư viện phụ thuộc**

```bash
   pip install -r requirements.txt
```

5. **Tạo file cấu hình môi trường**

   Copy file `.env.example` thành `.env`:

```bash
   cp .env.example .env      # macOS/Linux
   copy .env.example .env    # Windows
```

   Sau đó mở `.env` và cập nhật các giá trị sau:

   | Biến | Mô tả |
   |---|---|
   | `DATABASE_URL` | Chuỗi kết nối MySQL, format: `mysql+pymysql://<user>:<password>@<host>:<port>/<database_name>` |
   | `JWT_SECRET_KEY` | Khóa bí mật để ký JWT token, mỗi máy/môi trường nên dùng key riêng |
   | `JWT_ALGORITHM` | Thuật toán mã hóa JWT (mặc định `HS256`, thường không cần đổi) |
   | `ACCESS_TOKEN_EXPIRE_MINUTES` | Thời gian hết hạn access token, tính bằng phút |

   Để tạo `JWT_SECRET_KEY` ngẫu nhiên, dùng một trong các lệnh sau:

```bash
   openssl rand -hex 32
```

   hoặc bằng Python:

```bash
   python -c "import secrets; print(secrets.token_hex(32))"
```

6. **Tạo database MySQL**

   Đảm bảo database đã tồn tại trước khi chạy server (tên khớp với `DATABASE_URL` trong `.env`):

```sql
   CREATE DATABASE drowsiness_db;
```

   > Dự án dùng Alembic để quản lý migration, phải chạy thêm lệnh: `alembic upgrade head`

7. **Chạy server**

```bash
   uvicorn app.main:app --reload
```

   Server sẽ chạy tại: `http://127.0.0.1:8000`

   Swagger UI (API docs): `http://127.0.0.1:8000/docs`

### Lưu ý

Luôn kích hoạt `venv` trước khi chạy `uvicorn`, nếu không sẽ gặp lỗi `ModuleNotFoundError` do dùng nhầm Python hệ thống.
Không commit file `.env` lên Git — chỉ commit `.env.example`.
Mỗi thành viên nên tự tạo `JWT_SECRET_KEY` riêng cho môi trường local của mình.
Sau khi cài thêm thư viện mới, nhớ cập nhật lại `requirements.txt`:

```bash
  pip freeze > requirements.txt
```