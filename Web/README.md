# DriverGuard Web Admin

Giao diện quản trị React/Vite cho hệ thống DriverGuard. Frontend sử dụng JWT
Admin và kết nối FastAPI qua biến `VITE_API_BASE_URL`.

## Chạy local

```powershell
Copy-Item .env.example .env
npm install
npm run dev
```

Mặc định Frontend chạy tại `http://localhost:5173` và Backend tại
`http://localhost:8000/api/v1`.

## Production build

```powershell
npm run build
```

## Vị trí xe và cảnh báo tai nạn

- Dashboard hiển thị GPS mới nhất của các phiên active qua `GET /locations/latest`, tự tải lại sau mỗi 10 giây. GPS quá một phút được đánh dấu cũ; khi lỗi mạng, giữ dữ liệu lần tải thành công trước và tự thử lại.
- Chọn **Xem lịch trình** trên Dashboard hoặc trang **Phiên giám sát** để xem toàn bộ tọa độ, tốc độ, thời điểm và đường nối GPS theo thứ tự thời gian (`GET /locations?session_id=...`). Đường nối thể hiện các mẫu GPS, không phải tuyến đường được tính theo mạng lưới đường bộ.
- Trang **Cảnh báo** hỗ trợ lọc Buồn ngủ / Tai nạn. Chi tiết cảnh báo hiển thị tọa độ và điểm đỏ trên lịch trình; cập nhật trạng thái, ghi chú tiếp tục sử dụng API xử lý cảnh báo hiện có.
- `POST /locations` do ứng dụng gửi dữ liệu; trang quản trị chỉ đọc GPS bằng token admin.
- Bản đồ dùng Leaflet + MapLibre và nền OpenFreeMap, cần truy cập internet tới `tiles.openfreemap.org`. Trình duyệt không hỗ trợ WebGL dùng nền raster OpenStreetMap dự phòng. Khi lỗi có nút **Thử tải lại bản đồ**. Nếu nền bản đồ không tải được, bảng tọa độ và đường GPS vẫn dùng được.
