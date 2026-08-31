# API Contract v4

Backend là nơi duy nhất truy cập MySQL. Mobile và Web chỉ gọi API.

> **Thay đổi so với v3**:
> - Bổ sung đầy đủ Dashboard, Global Search, Reports, Audit Logs (trước đây liệt kê ở mục "chưa có").
> - `Users` response đổi sang camelCase (`fullName`, `isActive`, `createdAt`, `updatedAt`) để đồng nhất với các module khác.
> - `Monitoring Sessions` bổ sung `GET` (danh sách + chi tiết), trước đây chỉ có `POST`/`PATCH`.
> - Backend đã bật CORS cho Web Admin — nếu FE gọi bị chặn CORS, báo lại origin đang dùng để bổ sung whitelist.
> - Toàn bộ endpoint mutation (trừ 2 endpoint dành cho Edge) hiện đã ghi `audit_logs` tự động.
> - Lưu ý bảo mật (giữ nguyên từ v3): `GET /detection-settings/effective` và `POST /device-health/heartbeat`
>   KHÔNG yêu cầu token admin (dành cho Edge gọi trực tiếp), hệ thống CHƯA có cơ chế xác thực thiết bị riêng —
>   sẽ bổ sung device authentication (device secret/API key) trước khi lên production.

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
Lưu ý: request body vẫn dùng snake_case (`full_name`, `is_active`), nhưng **response trả về camelCase** — đây là điểm khác biệt cần chú ý khi tích hợp.

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

`DELETE /api/v1/vehicles/{id}` → trả về `204 No Content`. Đây là bảng duy nhất cho phép xóa cứng.

---

## Monitoring Sessions

### Danh sách (mới bổ sung ở v4)

`GET /api/v1/monitoring-sessions?user_id=...&device_id=...&status=active`

### Xem chi tiết (mới bổ sung ở v4)

`GET /api/v1/monitoring-sessions/{id}`

### Mở phiên giám sát

`POST /api/v1/monitoring-sessions`

Gọi khi camera được bật lên. Trả về `id` (UUID thật) để dùng cho các event tiếp theo — **không tự sinh chuỗi tùy ý ở client**.

Request:
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

Request:
```json
{
  "endedAt": "2026-08-20T20:15:00+07:00"
}
```

### Response format (áp dụng cho cả 4 endpoint trên)

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
`status` nhận: `active`, `ended`.

---

## Drowsiness Events

### Tạo cảnh báo

`POST /api/v1/drowsiness-events`

`sessionId` phải là UUID hợp lệ trả về từ `POST /api/v1/monitoring-sessions`.

Request:
```json
{
  "sessionId": "9c1a2b3d-...",
  "eventType": "DROWSINESS",
  "ear": 0.18,
  "confidence": 0.91,
  "closedDurationMs": 1800,
  "imageUrl": "https://.../evidence/xxxx.jpg",
  "source": "ANDROID",
  "occurredAt": "2026-08-20T19:30:00+07:00"
}
```

Response: bản ghi vừa tạo, `status` mặc định `"NEW"`, `handledBy` và `note` là `null`.

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
      "eventType": "DROWSINESS",
      "ear": 0.18,
      "confidence": 0.91,
      "closedDurationMs": 1800,
      "imageUrl": "https://.../evidence/xxxx.jpg",
      "occurredAt": "2026-08-20T19:30:00+07:00",
      "status": "NEW",
      "handledBy": null,
      "note": null
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

### Xử lý cảnh báo (chỉ Admin)

`PATCH /api/v1/drowsiness-events/{id}/status`

Request:
```json
{
  "status": "ACKNOWLEDGED",
  "note": "Đã xác nhận, theo dõi thêm"
}
```

`status` nhận 1 trong: `NEW`, `ACKNOWLEDGED`, `RESOLVED`. `handledBy` được backend tự gán từ token admin đang đăng nhập, client không truyền lên.

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

## Dashboard

Dùng cho màn hình tổng quan của Web Admin.

### Số liệu tổng hợp

`GET /api/v1/dashboard/summary`

Response:
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

### Xu hướng cảnh báo theo ngày

`GET /api/v1/dashboard/alert-trend?days=7`

`days`: số ngày gần nhất muốn xem (mặc định 7, tối đa 90).

Response:
```json
[
  { "date": "2026-08-16", "count": 3 },
  { "date": "2026-08-17", "count": 1 }
]
```
Lưu ý: chỉ trả về ngày có phát sinh cảnh báo, **không tự điền 0** cho ngày không có dữ liệu — FE cần tự xử lý nếu muốn biểu đồ liên tục.

### Danh sách cảnh báo mới nhất

`GET /api/v1/dashboard/recent-alerts?limit=5`

Response:
```json
[
  {
    "id": "e7f2a1c0-...",
    "sessionId": "9c1a2b3d-...",
    "eventType": "DROWSINESS",
    "occurredAt": "2026-08-22T10:00:00",
    "status": "NEW"
  }
]
```

---

## Global Search

`GET /api/v1/search?q=...&limit=20`

Tìm kiếm chéo theo tên/SĐT/username (users), mã/tên thiết bị (devices), biển số/tên xe (vehicles).

Response:
```json
[
  { "type": "user", "id": "b3f1c2a0-...", "title": "Nguyễn Văn A", "subtitle": "0901234567" },
  { "type": "device", "id": "d9e4f0b1-...", "title": "CAM-001", "subtitle": "Camera xe 51F-12345" },
  { "type": "vehicle", "id": "v1a2b3c4-...", "title": "51F-12345", "subtitle": "Xe máy đi làm" }
]
```
`type` nhận: `user`, `device`, `vehicle`. Không tìm thấy → trả về mảng rỗng `[]`.

---

## Reports

### Xuất báo cáo cảnh báo (CSV)

`GET /api/v1/reports/export?fromDate=...&toDate=...&deviceId=...&userId=...`

Trả về file **CSV** (`Content-Disposition: attachment`), mở được bằng Excel. Tất cả query param đều optional, có thể kết hợp để lọc.

Các cột trong file: `id, session_id, event_type, ear, confidence, closed_duration_ms, status, handled_by, note, occurred_at`.

---

## Audit Logs

Nhật ký thao tác của Admin, ghi tự động sau mỗi hành động tạo/sửa/xóa thành công.

`GET /api/v1/audit-logs?admin_id=...&target_table=...&page=1&page_size=20`

Response:
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

Danh sách `action` hiện có: `create_user`, `update_user`, `update_device`, `create_binding`, `auto_unbind_device`, `unbind_device`, `create_vehicle`, `update_vehicle`, `delete_vehicle`, `create_detection_settings`, `update_detection_settings`.

Lưu ý: 2 endpoint dành cho Edge (`detection-settings/effective`, `device-health/heartbeat`) **không** ghi audit log vì không phải hành động của admin.

---

Contract này sẽ tiếp tục cập nhật khi Backend có thay đổi. Toàn bộ endpoint (trừ các endpoint có ghi chú riêng dành cho Edge) yêu cầu Web Admin đã đăng nhập và gắn `Authorization: Bearer <token>`.