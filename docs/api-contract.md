# API Contract v5

Backend là nơi duy nhất truy cập MySQL. Mobile và Web chỉ gọi API.

> **Thay đổi so với v4**: bổ sung Locations (định vị GPS). `drowsiness_events` bổ sung
> `latitude`/`longitude` (vị trí tại thời điểm xảy ra), `eventType` giờ nhận thêm giá trị
> `"ACCIDENT"` bên cạnh `"DROWSINESS"` (vẫn là chuỗi tự do, không phải enum cứng).
> Lưu ý bảo mật (giữ nguyên từ v3): `GET /detection-settings/effective`, `POST /device-health/heartbeat`
> và nay thêm `POST /locations` đều KHÔNG yêu cầu token admin (dành cho App/Edge gọi trực tiếp),
> hệ thống CHƯA có cơ chế xác thực thiết bị riêng — sẽ bổ sung device authentication trước khi
> lên production.

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

### Response format (áp dụng cho cả 4 endpoint trên)
```json
{
  "id": "b3f1c2a0-...",
  "username": null,
  "phone": "0901234567",
  "fullName": "Nguyễn Văn A",
  "role": "driver",
  "isActive": true,
  "createdAt": "2026-08-22T10:00:00",
  "updatedAt": "2026-08-22T10:00:00"
}
```
Lưu ý: request body dùng snake_case, response trả về camelCase.

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

### Hủy gán
`PATCH /api/v1/device-bindings/{id}/unbind`

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
{ "licensePlate": "51F-99999" }
```

### Xóa
`DELETE /api/v1/vehicles/{id}` → trả về `204 No Content`. Bảng duy nhất cho phép xóa cứng.

---

## Monitoring Sessions

### Danh sách
`GET /api/v1/monitoring-sessions?user_id=...&device_id=...&status=active`

### Xem chi tiết
`GET /api/v1/monitoring-sessions/{id}`

### Mở phiên giám sát
`POST /api/v1/monitoring-sessions`
```json
{
  "userId": "b3f1c2a0-...",
  "deviceId": "d9e4f0b1-...",
  "vehicleId": null,
  "startedAt": "2026-08-20T19:00:00+07:00"
}
```

### Kết thúc phiên giám sát
`PATCH /api/v1/monitoring-sessions/{id}/end`
```json
{ "endedAt": "2026-08-20T20:15:00+07:00" }
```

### Response format
```json
{
  "id": "9c1a2b3d-...",
  "userId": "b3f1c2a0-...",
  "deviceId": "d9e4f0b1-...",
  "vehicleId": null,
  "status": "active",
  "startedAt": "2026-08-20T19:00:00+07:00",
  "endedAt": null
}
```

---

## Drowsiness Events

### Tạo cảnh báo
`POST /api/v1/drowsiness-events`

`sessionId` phải là UUID hợp lệ trả về từ `POST /api/v1/monitoring-sessions`. `eventType` nhận `"DROWSINESS"` hoặc `"ACCIDENT"`. `latitude`/`longitude` optional — nên gửi kèm nếu App có định vị tại thời điểm xảy ra.

Request:
```json
{
  "sessionId": "9c1a2b3d-...",
  "eventType": "ACCIDENT",
  "ear": null,
  "confidence": 0.95,
  "closedDurationMs": null,
  "imageUrl": "https://.../evidence/xxxx.jpg",
  "source": "ANDROID",
  "occurredAt": "2026-08-20T19:30:00+07:00",
  "latitude": 10.7626,
  "longitude": 106.6602
}
```

Response: bản ghi vừa tạo, `status` mặc định `"NEW"`, `handledBy`/`note` là `null`, kèm `latitude`/`longitude` vừa gửi.

### Lấy lịch sử cảnh báo
`GET /api/v1/drowsiness-events`

Query params: `deviceId`, `userId`, `status`, `page`, `pageSize`.

Response:
```json
{
  "items": [
    {
      "id": "e7f2a1c0-...",
      "sessionId": "9c1a2b3d-...",
      "eventType": "ACCIDENT",
      "ear": null,
      "confidence": 0.95,
      "closedDurationMs": null,
      "imageUrl": "https://.../evidence/xxxx.jpg",
      "occurredAt": "2026-08-20T19:30:00+07:00",
      "status": "NEW",
      "handledBy": null,
      "note": null,
      "latitude": 10.7626,
      "longitude": 106.6602
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

### Xử lý cảnh báo (chỉ Admin)
`PATCH /api/v1/drowsiness-events/{id}/status`
```json
{
  "status": "ACKNOWLEDGED",
  "note": "Đã xác nhận, theo dõi thêm"
}
```
`status` nhận: `NEW`, `ACKNOWLEDGED`, `RESOLVED`. `handledBy` tự gán từ token admin, client không truyền lên.

---

## Locations (GPS) — MỚI

Theo dõi vị trí xe trong lúc giám sát, và vị trí tại thời điểm xảy ra cảnh báo (xem field `latitude`/`longitude` trong Drowsiness Events ở trên).

### Ghi nhận 1 điểm định vị

`POST /api/v1/locations`

**Không yêu cầu token admin** — gọi trực tiếp bởi App trong lúc `monitoring_session` đang `active`, định kỳ (khuyến nghị mỗi 10–30 giây).

```json
{
  "sessionId": "9c1a2b3d-...",
  "deviceId": "d9e4f0b1-...",
  "latitude": 10.7626,
  "longitude": 106.6602,
  "speedKmh": 42.5,
  "recordedAt": "2026-08-22T10:05:00+07:00"
}
```

Response:
```json
{
  "id": "loc1-...",
  "sessionId": "9c1a2b3d-...",
  "deviceId": "d9e4f0b1-...",
  "latitude": 10.7626,
  "longitude": 106.6602,
  "speedKmh": 42.5,
  "recordedAt": "2026-08-22T10:05:00+07:00"
}
```

### Xem lịch trình di chuyển của 1 phiên

`GET /api/v1/locations?session_id=...` *(yêu cầu admin)*

Trả về toàn bộ điểm GPS của phiên, sắp xếp theo thời gian tăng dần — dùng để vẽ đường đi trên bản đồ khi tra cứu 1 cảnh báo/phiên cụ thể.

### Vị trí hiện tại của các xe đang hoạt động

`GET /api/v1/locations/latest` *(yêu cầu admin)*

Trả về điểm GPS gần nhất của mỗi thiết bị đang có `monitoring_session` ở trạng thái `active` — phục vụ bản đồ tổng quan trên Dashboard.

```json
[
  {
    "deviceId": "d9e4f0b1-...",
    "sessionId": "9c1a2b3d-...",
    "latitude": 10.7626,
    "longitude": 106.6602,
    "speedKmh": 42.5,
    "recordedAt": "2026-08-22T10:05:00+07:00"
  }
]
```

---

## Detection Settings

Ngưỡng cấu hình để AI xác định buồn ngủ. Có thể set mặc định toàn hệ thống (`deviceId = null`) hoặc override riêng theo từng thiết bị.

`GET /api/v1/detection-settings` *(yêu cầu admin)*

`GET /api/v1/detection-settings/effective?device_id=...` — **không yêu cầu token admin**, ưu tiên trả override riêng, fallback về mặc định.

`POST /api/v1/detection-settings` *(yêu cầu admin, upsert)*
```json
{
  "deviceId": null,
  "earThreshold": 0.2,
  "confidenceThreshold": 0.85,
  "closedDurationThresholdMs": 1500
}
```

`PATCH /api/v1/detection-settings/{id}` *(yêu cầu admin)*
```json
{ "earThreshold": 0.22 }
```

---

## Device Health

Trạng thái kết nối thiết bị, tách biệt với `drowsiness_events`.

`POST /api/v1/device-health/heartbeat` — **không yêu cầu token admin**, tự cập nhật `devices.status`/`last_seen_at`.
```json
{
  "deviceCode": "CAM-001",
  "status": "connected",
  "note": null
}
```
`status` nhận: `connected`, `warning`.

`GET /api/v1/device-health?device_id=...` *(yêu cầu admin, tối đa 100 bản ghi gần nhất)*

---

## Dashboard

`GET /api/v1/dashboard/summary`
```json
{
  "totalDevices": 12,
  "onlineDevices": 9,
  "offlineDevices": 3,
  "sessionsToday": 25,
  "alertsToday": 4,
  "unhandledAlerts": 2
}
```

`GET /api/v1/dashboard/alert-trend?days=7`
```json
[{ "date": "2026-08-16", "count": 3 }]
```

`GET /api/v1/dashboard/recent-alerts?limit=5`
```json
[{
  "id": "e7f2a1c0-...",
  "sessionId": "9c1a2b3d-...",
  "eventType": "DROWSINESS",
  "occurredAt": "2026-08-22T10:00:00",
  "status": "NEW"
}]
```

---

## Global Search

`GET /api/v1/search?q=...&limit=20`
```json
[
  { "type": "user", "id": "...", "title": "Nguyễn Văn A", "subtitle": "0901234567" },
  { "type": "device", "id": "...", "title": "CAM-001", "subtitle": "Camera xe 51F-12345" },
  { "type": "vehicle", "id": "...", "title": "51F-12345", "subtitle": "Xe máy đi làm" }
]
```

---

## Reports

`GET /api/v1/reports/export?fromDate=...&toDate=...&deviceId=...&userId=...`

Trả về file CSV, mở được bằng Excel. Cột: `id, session_id, event_type, ear, confidence, closed_duration_ms, status, handled_by, note, occurred_at`.

---

## Audit Logs

`GET /api/v1/audit-logs?admin_id=...&target_table=...&page=1&page_size=20`
```json
{
  "id": "aud1-...",
  "adminId": "b3f1c2a0-...",
  "action": "update_device",
  "targetTable": "devices",
  "targetId": "d9e4f0b1-...",
  "beforeValue": { "status": "online" },
  "afterValue": { "status": "locked" },
  "createdAt": "2026-08-22T10:00:00"
}
```
`action` hiện có: `create_user`, `update_user`, `update_device`, `create_binding`, `auto_unbind_device`, `unbind_device`, `create_vehicle`, `update_vehicle`, `delete_vehicle`, `create_detection_settings`, `update_detection_settings`.

---

Contract này sẽ tiếp tục cập nhật khi Backend có thay đổi. Endpoint dành cho App/Edge (không yêu cầu token): `POST /locations`, `POST /device-health/heartbeat`, `GET /detection-settings/effective`.