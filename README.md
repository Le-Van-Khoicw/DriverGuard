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
