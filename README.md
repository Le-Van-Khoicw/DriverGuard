# DriverGuard — Điều phối vận tải và giám sát tài xế

Ứng dụng khóa luận cho một đội xe tải đường dài: quản lý đơn vận chuyển, phân công
chuyến, ghi nhận kết quả nhận/giao hàng và cảnh báo dấu hiệu nhắm mắt kéo dài.

## Người dùng

- **Admin / quản lý:** duyệt tài xế, quản lý xe, cấp quyền nhân viên, duyệt nghỉ.
- **Điều phối viên:** tạo đơn, xếp điểm dừng, giao/đổi tài xế, xử lý kết quả giao có vấn đề.
- **Tài xế Android:** nhận chuyến, bắt đầu, giám sát, cập nhật từng điểm và kết thúc.

Admin và điều phối dùng chung một web, có phân quyền. Không còn đăng nhập demo
bằng mật khẩu tùy ý. Tài khoản nhân viên phải tồn tại trong Firebase Authentication
và được cấp quyền trong `staff/{uid}`.

## Cấu trúc đang dùng

```text
Web/                       React + TypeScript, cổng vận tải
Mobile/DriverGuard/        Kotlin + Compose, ứng dụng tài xế
Firebase/functions/       Máy chủ kiểm tra và thực hiện lệnh điều phối
Firebase/firestore.rules  Quy tắc quyền dữ liệu
AI/                       Python, gán nhãn/đánh giá thuật toán mắt từ video
docs/                     Nghiệp vụ, công thức điểm, hướng dẫn chạy
```

Nguồn dữ liệu ứng dụng là Firebase Auth + Firestore. `Backend/` FastAPI/MySQL và
`Edge/` là phần kiến trúc cũ/thử nghiệm, không phải máy chủ của luồng vận tải mới.

## Chạy thật trên mạng (phương án đang triển khai)

Web dùng Firebase Hosting; backend Node dùng Render; Auth và Firestore dùng project
thật `driverguard-854e5`. Giấy tờ lưu Supabase Storage Private, không cần Firebase Storage/Blaze.
Xem [hướng dẫn triển khai online](docs/deploy-online.md) và `render.yaml`.
Backend cần URL và secret trên Render trước khi web/APK online dùng được.

```powershell
.\Configure-Online.ps1 -ApiUrl https://<ten-api>.onrender.com
.\Start-Online.ps1
.\Build-OnlineAndroid.ps1
```

## Bộ emulator tùy chọn, tách biệt Firebase thật

Đã cấu hình máy chủ Firebase Emulator, web và APK Local riêng. Chạy:

```powershell
.\Start-Local.ps1
```

Mở `http://127.0.0.1:5173`. Xem [tài khoản demo và cách cài Android Local](docs/run-local.md).
Dừng bằng `Stop-Local.ps1` để xuất dữ liệu trước khi tắt. Không cần Blaze.

## Đọc và chạy

1. [Nghiệp vụ mục tiêu](docs/logistics-workflow.md).
2. [Phần đã viết, cách chạy emulator và giới hạn](docs/logistics-implementation.md).
3. [Công thức điểm quan sát và lưu offline](docs/trip-scoring.md).
4. [Công cụ Python đánh giá mắt](AI/README.md).

Web: `cd Web`, `npm ci`, `npm run dev`.
Android: mở `Mobile/DriverGuard` bằng Android Studio.
Luồng vận tải cần backend online: xem `docs/deploy-online.md` trước khi chạy.
Chưa có URL Render thực tế; chưa nghiệm thu xuyên suốt trên cloud.

## Phần AI

MediaPipe xác định điểm mốc mắt trên điện thoại; thuật toán EAR và thời gian nhắm mắt
phát hiện dấu hiệu cần cảnh báo. Không cần RAG hoặc fine-tuning ngôn ngữ cho nhiệm vụ này.
Điểm hiện tại là công thức thử nghiệm, có điều kiện đủ dữ liệu và độ bao phủ; không
phải xác suất tai nạn hay chứng nhận tài xế an toàn. Camera hoạt động không cần mạng.

Kết quả giao hàng và kết quả quan sát là hai báo cáo riêng, liên kết cùng chuyến.
