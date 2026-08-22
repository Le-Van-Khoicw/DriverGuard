# API Contract v1

> **Tóm tắt các thay đổi**: bổ sung Auth, Monitoring Sessions (bắt buộc phải mở session trước khi gửi event),
> cập nhật Drowsiness Events để khớp với luồng xử lý cảnh báo (status/handled_by/note).
> Các endpoint còn lại (users, devices, device_bindings, vehicles, detection_settings, device_health,
> dashboard, global_search, reports, audit_logs) sẽ được bổ sung khi Backend triển khai từng module.

---

## Auth

### Đăng nhập (Admin)

`POST /api/auth/login`

Request:
{
  "username": "admin01",
  "password": "your_password"
}

Response:
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "bearer",
  "expiresIn": 3600
}

Mọi endpoint bên dưới (trừ endpoint Edge/Mobile gửi dữ liệu giám sát) yêu cầu header:
Authorization: Bearer <accessToken>

---

## Monitoring Sessions

### Mở phiên giám sát

`POST /api/monitoring-sessions`

Gọi khi camera được bật lên. Trả về `sessionId` (UUID thật) để dùng cho các event tiếp theo — **không tự sinh chuỗi tùy ý ở client**.

Request:
{
  "userId": "b3f1c2a0-...",
  "deviceId": "d9e4f0b1-...",
  "vehicleId": null,
  "startedAt": "2026-08-20T19:00:00+07:00"
}

Response:
{
  "id": "9c1a2b3d-...",
  "status": "active",
  "startedAt": "2026-08-20T19:00:00+07:00"
}

### Kết thúc phiên giám sát

`PATCH /api/monitoring-sessions/{id}/end`

Request:
{
  "endedAt": "2026-08-20T20:15:00+07:00"
}

Response:
{
  "id": "9c1a2b3d-...",
  "status": "ended",
  "endedAt": "2026-08-20T20:15:00+07:00"
}

---

## Drowsiness Events

### Tạo cảnh báo

`POST /api/drowsiness-events`

`sessionId` phải là UUID hợp lệ trả về từ `POST /api/monitoring-sessions`.

Request:
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

Response: bản ghi vừa tạo, `status` mặc định `"NEW"`, `handledBy` và `note` là `null`.
{
  "id": "e7f2a1c0-...",
  "sessionId": "9c1a2b3d-...",
  "eventType": "DROWSINESS",
  "ear": 0.18,
  "confidence": 0.91,
  "closedDurationMs": 1800,
  "imageUrl": "https://.../evidence/xxxx.jpg",
  "source": "ANDROID",
  "occurredAt": "2026-08-20T19:30:00+07:00",
  "status": "NEW",
  "handledBy": null,
  "note": null
}

### Lấy lịch sử cảnh báo

`GET /api/drowsiness-events`

Query params: `from`, `to`, `deviceId`, `userId`, `vehicleId`, `status`, `page`, `pageSize`.

Response:
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

### Xử lý cảnh báo (chỉ Admin)

`PATCH /api/drowsiness-events/{id}/status`

Request:
{
  "status": "ACKNOWLEDGED",
  "note": "Đã xác nhận, theo dõi thêm"
}

`status` nhận 1 trong: `NEW`, `ACKNOWLEDGED`, `RESOLVED`. `handledBy` được backend tự gán từ token admin đang đăng nhập, client không truyền lên.

Response:
{
  "id": "e7f2a1c0-...",
  "status": "ACKNOWLEDGED",
  "handledBy": "b3f1c2a0-...",
  "note": "Đã xác nhận, theo dõi thêm"
}

---

## Các endpoint còn thiếu, sẽ bổ sung theo tiến độ Backend

- `GET/POST/PATCH /api/users`
- `GET/POST/PATCH /api/devices`
- `POST /api/device-bindings`, `PATCH /api/device-bindings/{id}/unbind`
- `GET/POST/PATCH/DELETE /api/vehicles`
- `GET/PATCH /api/detection-settings`
- `POST /api/device-health/heartbeat`, `GET /api/device-health`
- `GET /api/dashboard/summary`
- `GET /api/search?q=...`
- `GET /api/reports/export`
- `GET /api/audit-logs`

Contract này sẽ tiếp tục cập nhật khi Backend triển khai từng module.