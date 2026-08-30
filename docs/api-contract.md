# API Contract v3

Backend là nơi duy nhất truy cập MySQL. Mobile và Web chỉ gọi API.

> **Thay đổi so với v2**: bổ sung Detection Settings, Device Health.
> Lưu ý bảo mật: 2 endpoint `GET /detection-settings/effective` và `POST /device-health/heartbeat`
> hiện KHÔNG yêu cầu token admin (dành cho Edge gọi trực tiếp), và hệ thống CHƯA có cơ chế xác thực
> thiết bị riêng — sẽ bổ sung device authentication (device secret/API key) trước khi lên production.
> Các endpoint còn lại (dashboard, global_search, reports, audit_logs) sẽ được bổ sung khi Backend
> triển khai tiếp.

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

Mọi endpoint bên dưới (trừ endpoint dành riêng cho Edge, có ghi chú cụ thể) yêu cầu header:
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

## Detection Settings

Ngưỡng cấu hình để AI xác định buồn ngủ. Có thể set mặc định toàn hệ thống (`deviceId = null`) hoặc override riêng theo từng thiết bị.

### Danh sách toàn bộ cấu hình

`GET /api/v1/detection-settings` *(yêu cầu admin)*

### Lấy ngưỡng đang áp dụng cho 1 thiết bị

`GET /api/v1/detection-settings/effective?device_id=...`

**Không yêu cầu token admin** — dùng cho Edge tự đồng bộ ngưỡng định kỳ. Ưu tiên trả về override riêng của thiết bị, nếu không có thì trả về cấu hình mặc định toàn hệ thống.

Response:
```json
{
  "id": "f1a2b3c4-...",
  "deviceId": null,
  "earThreshold": 0.2,
  "confidenceThreshold": 0.85,
  "closedDurationThresholdMs": 1500,
  "updatedAt": "2026-08-22T10:00:00"
}
```

### Tạo/cập nhật cấu hình (upsert)

`POST /api/v1/detection-settings` *(yêu cầu admin)*

```json
{
  "deviceId": null,
  "earThreshold": 0.2,
  "confidenceThreshold": 0.85,
  "closedDurationThresholdMs": 1500
}
```
Nếu `deviceId` (hoặc cấu hình global khi `deviceId = null`) đã tồn tại, API tự động cập nhật thay vì tạo bản ghi trùng.

### Cập nhật theo id

`PATCH /api/v1/detection-settings/{id}` *(yêu cầu admin)*

```json
{
  "earThreshold": 0.22
}
```

---

## Device Health

Trạng thái kết nối thiết bị, tách biệt với `drowsiness_events`.

### Gửi heartbeat

`POST /api/v1/device-health/heartbeat`

**Không yêu cầu token admin** — gọi trực tiếp bởi Edge/thiết bị. Đồng thời tự cập nhật `devices.status` và `devices.last_seen_at`.

```json
{
  "deviceCode": "CAM-001",
  "status": "connected",
  "note": null
}
```
`status` nhận: `connected`, `warning`.

Response:
```json
{
  "id": "a1b2c3d4-...",
  "deviceId": "d9e4f0b1-...",
  "status": "connected",
  "lastHeartbeatAt": "2026-08-22T10:00:00",
  "note": null,
  "createdAt": "2026-08-22T10:00:00"
}
```

### Lịch sử heartbeat

`GET /api/v1/device-health?device_id=...` *(yêu cầu admin, trả tối đa 100 bản ghi gần nhất)*

---

## Các endpoint còn thiếu, sẽ bổ sung theo tiến độ Backend

- `GET /api/v1/dashboard/summary`
- `GET /api/v1/search?q=...`
- `GET /api/v1/reports/export`
- `GET /api/v1/audit-logs`

Contract này sẽ tiếp tục cập nhật khi Backend triển khai từng module.