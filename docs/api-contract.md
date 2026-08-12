# API Contract v0

Backend là nơi duy nhất truy cập MySQL. Mobile và Web chỉ gọi API.

## Tạo cảnh báo

`POST /api/drowsiness-events`

Request mẫu:

```json
{
  "sessionId": "session-001",
  "eventType": "DROWSINESS",
  "ear": 0.18,
  "confidence": 0.91,
  "closedDurationMs": 1800,
  "source": "ANDROID",
  "occurredAt": "2026-08-12T19:30:00+07:00"
}
```

## Lấy lịch sử cảnh báo

`GET /api/drowsiness-events`

Response mẫu:

```json
{
  "items": [],
  "total": 0
}
```

Contract này sẽ được cập nhật khi Backend bắt đầu triển khai.
