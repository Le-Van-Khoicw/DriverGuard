# API Contract v2

Backend là nơi duy nhất truy cập MySQL. Mobile và Web chỉ gọi API.

> **Thay đổi so với v1**: bổ sung Users, Devices, Device Bindings, Vehicles.
> Auth chuyển từ email sang username. Các endpoint còn lại (detection_settings, device_health,
> dashboard, global_search, reports, audit_logs) sẽ được bổ sung khi Backend triển khai tiếp.

---

## Auth

### Đăng nhập (Admin)

`POST /api/v1/auth/login`

Request (form-urlencoded, theo chuẩn OAuth2PasswordRequestForm):
username=admin
password=your_password


Response:
```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "bearer"
}
```

Mọi endpoint bên dưới (trừ endpoint Edge/Mobile gửi dữ liệu giám sát, nếu có) yêu cầu header:
Authorization: Bearer <accessToken>

---

## Users

### Danh sách tài khoản

`GET /api/v1/users?role=driver`

### Xem chi tiết

`GET /api/v1/users/{id}`

### Tạo tài khoản

`POST /api/v1/users`

Request:
```json
{
  "full_name": "Nguyễn Văn A",
  "phone": "0901234567",
  "username": null,
  "role": "driver",
  "is_active": true,
  "password": null
}
```
Lưu ý: `role = "admin"` bắt buộc phải có `password`. `role = "driver"` nên để `username`/`password` là `null`.

### Cập nhật (đổi tên, khóa/mở khóa)

`PATCH /api/v1/users/{id}`
```json
{
  "full_name": "Nguyễn Văn A (đã cập nhật)",
  "is_active": false
}
```

---

## Devices

### Danh sách thiết bị

`GET /api/v1/devices?status=online`

### Xem chi tiết

`GET /api/v1/devices/{id}`

### Thêm thiết bị (bằng mã kích hoạt/QR)

`POST /api/v1/devices`
```json
{
  "deviceCode": "CAM-001",
  "deviceName": "Camera xe 51F-12345",
  "deviceType": "dashcam"
}
```

### Cập nhật (đổi tên, khóa/mở khóa)

`PATCH /api/v1/devices/{id}`
```json
{
  "deviceName": "Camera xe 51F-12345 (mới)",
  "status": "locked"
}
```
`status` nhận: `online`, `offline`, `locked`.

---

## Device Bindings

Quan hệ camera – tài khoản theo thời gian. **Mỗi thiết bị tại 1 thời điểm chỉ có tối đa 1 binding `active`** — khi gán thiết bị đã có chủ cho tài khoản khác, backend tự đóng binding cũ (`status → ended`, `unboundAt` = thời điểm gán mới), không cần unbind thủ công trước.

### Danh sách / lịch sử gán

`GET /api/v1/device-bindings?device_id=...&user_id=...&status=active`

### Gán thiết bị cho tài khoản

`POST /api/v1/device-bindings`
```json
{
  "userId": "b3f1c2a0-...",
  "deviceId": "d9e4f0b1-..."
}
```

Response:
```json
{
  "id": "9c1a2b3d-...",
  "userId": "b3f1c2a0-...",
  "deviceId": "d9e4f0b1-...",
  "status": "active",
  "boundAt": "2026-08-22T10:00:00",
  "unboundAt": null
}
```

### Hủy gán

`PATCH /api/v1/device-bindings/{id}/unbind`

Response: bản ghi với `status: "ended"`, `unboundAt` có giá trị.

---

## Vehicles

Thông tin phương tiện, thuộc về 1 tài khoản, tùy chọn khai báo.

### Danh sách

`GET /api/v1/vehicles?user_id=...`

### Xem chi tiết

`GET /api/v1/vehicles/{id}`

### Tạo

`POST /api/v1/vehicles`
```json
{
  "userId": "b3f1c2a0-...",
  "displayName": "Xe máy đi làm",
  "licensePlate": "51F-12345",
  "vehicleType": "motorbike"
}
```
`vehicleType` nhận: `motorbike`, `car`, `truck`, `bus`.

### Cập nhật

`PATCH /api/v1/vehicles/{id}`
```json
{
  "licensePlate": "51F-99999"
}
```

### Xóa

`DELETE /api/v1/vehicles/{id}` → trả về `204 No Content`.

---

## Monitoring Sessions

*(giữ nguyên như v1 — xem tài liệu trước, không lặp lại ở đây)*

## Drowsiness Events

*(giữ nguyên như v1 — xem tài liệu trước, không lặp lại ở đây)*

---

## Các endpoint còn thiếu, sẽ bổ sung theo tiến độ Backend

- `GET/PATCH /api/v1/detection-settings`
- `POST /api/v1/device-health/heartbeat`, `GET /api/v1/device-health`
- `GET /api/v1/dashboard/summary`
- `GET /api/v1/search?q=...`
- `GET /api/v1/reports/export`
- `GET /api/v1/audit-logs`

Contract này sẽ tiếp tục cập nhật khi Backend triển khai từng module.