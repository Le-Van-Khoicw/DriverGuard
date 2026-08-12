# DriverGuard Mobile

Ứng dụng Android mô phỏng hệ thống phát hiện buồn ngủ cho tài xế.

## Trạng thái hiện tại

- Đã tạo project Kotlin + Jetpack Compose.
- Đã tổ chức màn hình theo hướng MVVM.
- Có màn hình giám sát giả lập.
- Có nút bắt đầu, dừng và giả lập cảnh báo buồn ngủ.
- Đã khai báo quyền camera cho bước CameraX tiếp theo.

## Chạy project

Mở thư mục `Mobile/DriverGuard` bằng Android Studio, chờ Gradle sync rồi chạy module `app`.

## Luồng phát triển tiếp theo

1. Thêm CameraX preview.
2. Thêm MediaPipe để lấy landmark mắt.
3. Tính EAR trên Android.
4. Thay nút giả lập bằng kết quả AI.
5. Thêm Room và Retrofit.
