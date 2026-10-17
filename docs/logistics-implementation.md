# Luồng vận tải đã viết và cách chạy

## Trạng thái thực tế

Đã viết web quản trị/điều phối, tab Chuyến trên Android, callable `dispatchCommand`,
quy tắc Firestore và liên kết báo cáo quan sát với phân công. Chưa triển khai các
thay đổi này lên Firebase thật, chưa nghiệm thu luồng web → Android → máy chủ.
Đã khởi động máy chủ emulator local; biên dịch/khởi động thành công không chứng minh toàn bộ luồng nghiệp vụ đã hoạt động đúng.

Tài liệu `logistics-workflow.md` là yêu cầu mục tiêu. Bản này mô tả giới hạn của code.

## Vì sao xử lý giao chuyến ở máy chủ?

Lịch tài xế giống sổ đặt chỗ: hai điều phối có thể cùng nhìn thấy một tài xế rảnh.
Nếu chỉ kiểm tra trên giao diện, cả hai đều có thể giao chuyến. Vì vậy máy chủ đọc
lịch và ghi phân công trong cùng một transaction; thao tác tranh chấp phải đọc lại.

```text
Web (Firebase Auth + staff/{uid}) ─┐
                                 ├→ dispatchCommand → Firestore transaction
Android (Auth + drivers/{uid}) ───┘                      ↓
                                       đơn / chuyến / lịch / nghỉ / lịch sử
Android camera → phiên quan sát riêng → lưu máy → đồng bộ báo cáo + cảnh báo
```

Chấm điểm mắt vẫn chạy trên Android, không gọi API AI trả phí. Máy chủ điều phối
không xử lý video. `Backend/` FastAPI cũ không tham gia luồng mới này.

## Các file để đọc

| Phần | File tính từ thư mục dự án |
|---|---|
| Lệnh nghiệp vụ, kiểm tra quyền/lịch | `Firebase/functions/index.js` |
| Quyền truy cập dữ liệu | `Firebase/firestore.rules` |
| Tạo quản trị đầu tiên | `Firebase/functions/bootstrap-admin.mjs` |
| Đăng nhập web thật và kiểm tra vai trò | `Web/src/app/App.tsx` |
| Các màn hình vận tải | `Web/src/app/LogisticsPortal.tsx` |
| Schema và kết nối callable web | `Web/src/api/dispatch.ts` |
| Dữ liệu chuyến, lệnh offline Android | `Mobile/DriverGuard/app/src/main/java/com/example/driverguard/feature/dispatch/DispatchViewModel.kt` |
| Giao diện tài xế | `Mobile/DriverGuard/app/src/main/java/com/example/driverguard/feature/dispatch/DispatchScreen.kt` |
| Liên kết phiên quan sát | `Mobile/DriverGuard/app/src/main/java/com/example/driverguard/feature/monitoring/MonitoringViewModel.kt` |
| Công thức điểm thử nghiệm | `Mobile/DriverGuard/app/src/main/java/com/example/driverguard/feature/monitoring/ai/TripAiAnalyzer.kt` |

## Dữ liệu

- `staff/{authUid}`: vai trò `admin`/`dispatcher`, `active`, mã và tên nhân viên.
  Tài xế không được tự cấp vai trò. Nhân viên bị thu hồi quyền sẽ mất phiên web.
- `drivers/{authUid}`: hồ sơ Android; admin duyệt `accountStatus` và cấp mã nhân viên.
  Tài xế vẫn đăng ký/xác minh email từ app. Không tạo tài khoản giả bằng một profile.
- `fleetVehicles/{id}`: xe tải công ty, biển số, tải trọng kg, sẵn sàng/bảo trì/ngừng.
  Biển số duy nhất được giữ bằng `fleetVehiclePlates`. Không sửa xe khi còn lịch giữ.
- `transportOrders/{id}`: khách, gửi/nhận, địa chỉ, liên hệ, danh sách hàng, trọng lượng,
  thời gian sẵn sàng và hạn giao mong muốn. Một đơn một nơi lấy/một nơi giao.
- `dispatchTrips/{id}`: nhiều đơn, danh sách điểm dừng có thứ tự, người tạo/điều phối,
  xe, tài xế hiện tại, lịch dự kiến/thực tế, trạng thái, phiên bản và lịch sử bàn giao.
- `resourceCalendars/driver_{uid}` và `vehicle_{id}`: lịch giữ chỗ và nghỉ đã duyệt;
  ứng dụng không được ghi trực tiếp. Chuyến đang chạy vẫn giữ tài nguyên khi quá giờ.
- `leaveRequests`: khoảng thời gian nghỉ, lý do, trạng thái và người duyệt.
- `tripAssignments`: bằng chứng phân công bất biến theo chuyến/người/phiên bản;
  người đã bàn giao vẫn được gửi báo cáo quan sát lịch sử của mình.
- `dispatchReceipts`: kết quả thao tác theo UID + operationId, chống thực thi lại khi
  điện thoại gửi lại cùng lệnh. `dispatchAudit`: người thực hiện và thời gian máy chủ.
- `trips`: giữ tên collection cũ cho **báo cáo phiên quan sát**, ID là UUID phiên.
  Thêm `dispatchTripId` và `assignmentVersion`, không dùng UUID phiên làm mã chuyến hàng.
- `alerts`: thêm `dispatchTripId`, `monitoringSessionId`, `assignmentVersion`; `userId`
  vẫn là người thực sự ghi nhận cảnh báo. Không tự bịa độ tin cậy AI.

## Luồng đã viết

1. Admin duyệt tài xế, tạo xe và cấp quyền nhân viên theo UID Firebase Auth thật.
2. Điều phối tạo đơn, chọn đơn/xe/tài xế, xếp điểm nhận trước điểm giao rồi xác nhận.
   Máy chủ kiểm tra tài khoản, lịch trùng, nghỉ, trạng thái xe, tổng kg so với tải trọng.
3. Tài xế nhận/từ chối có lý do. Nhận và bắt đầu cần xác nhận máy chủ.
4. Khi đang thực hiện, mở giám sát từ chi tiết chuyến để liên kết đúng phân công.
   Tab Giám sát vẫn có phiên độc lập, được ghi rõ, không tính thành chuyến vận tải.
5. Ghi kết quả từng điểm theo thứ tự: đủ/thiếu/thất bại, số lượng từng mặt hàng,
   người nhận/giao thực tế và lý do. Không giao vượt số lượng đã lấy.
6. Kết thúc thành công chỉ khi mọi điểm đủ. Nếu thiếu/thất bại thì kết thúc có vấn đề;
   điều phối ghi hướng xử lý. Không biến kết quả thiếu thành đã giao đủ.
7. Nghỉ được duyệt làm chuyến chưa chạy bị ảnh hưởng chuyển sang cần giao lại.
   Chuyến đang chạy phải bàn giao/kết thúc trước khi duyệt nghỉ trùng thời gian hiện tại.
8. Đổi người trước khi chạy yêu cầu người mới nhận lại. Bàn giao khi đang chạy ghi
   vị trí/lý do/tình trạng hàng, giữ điểm đã làm, chờ tài xế mới nhận, tăng phiên bản.
9. Khi máy chủ báo mất quyền phân công, phiên quan sát cũ được chốt theo người cũ.
   Trên thiết bị đang offline, báo cáo lịch sử vẫn giữ; lệnh tiến độ cũ bị đối chiếu
   phiên bản khi đồng bộ và không được ghi đè người mới.

## Mất mạng

- Nhận/bắt đầu: không xác nhận thành công bằng dữ liệu cache.
- Kết quả điểm và yêu cầu kết thúc: ghi bền vững vào SharedPreferences trước,
  hiển thị chờ đồng bộ; gửi tuần tự với ID ổn định, thử lại định kỳ khi app còn chạy.
- Dùng Firestore cache cho danh sách chuyến đã tải. Chuyến chưa từng tải cần mạng.
- Lệnh bị từ chối do trạng thái/phân công giữ lại trên máy, dừng các lệnh phụ thuộc;
  tài xế liên hệ điều phối rồi xác nhận bỏ hàng đợi nếu cần nhập lại.
- Không có dịch vụ nền đồng bộ khi app bị đóng. Mở lại app sẽ đọc hàng đợi.
- Giám sát có lưu riêng; rời màn hình camera sẽ tạm dừng quan sát. Phiên mắt kết thúc
  không tự coi hàng hóa đã giao. Báo cáo mắt chưa đủ dữ liệu giữ điểm `null`.

## Chạy miễn phí trên máy — phương án đã chọn

Đã cấu hình project giả lập `demo-driverguard`, cài Firebase CLI và dựng APK Local
riêng (`com.example.driverguard.local`). Xem [hướng dẫn chạy local](run-local.md).

```powershell
cd D:\TTTN
.\Start-Local.ps1
```

Script khởi động Auth/Firestore/Functions emulator, tạo tài khoản quản lý/điều phối/
tài xế local còn thiếu, rồi mở web server cổng 5173. Tự import dữ liệu từ
`Firebase/.emulator-data` nếu có. Không deploy lên Firebase thật.

```powershell
# Dựng Android cho Android Emulator
.\Build-LocalAndroid.ps1
# Dựng và cài cho điện thoại thật qua USB
.\Build-LocalAndroid.ps1 -PhoneUsb -Install
# Xuất dữ liệu và dừng (dùng cả khi server được mở từ Codex)
.\Stop-Local.ps1
```

Bản Android Local khởi tạo project emulator trong Application, trước các repository;
FirebaseInitProvider tự động bị tắt ở bản này để tránh kết nối sai môi trường.
App Local và app hiện tại dùng package/bộ nhớ riêng. Google Sign-In thật bị chặn trong
bản Local; dùng Email/Password local. Web chạy bằng Vite mode `thesis`.

Máy chủ đã khởi động được, Functions tải được định nghĩa lệnh, dữ liệu seed đã được
xuất/import. Web và APK Local dựng thành công. Chưa nghiệm thu chức năng nghiệp vụ
qua hai giao diện và chưa chạy kiểm thử tự động cho phần triển khai mới.

## Đưa lên Firebase thật

Chưa thực hiện. Cần chủ project cấu hình Authentication Email/Password, cấp admin
đầu tiên bằng Firebase Console hoặc script với Admin credentials trên máy tin cậy,
triển khai functions/rules/indexes, rồi mới phát hành client tương ứng.

Cloud Functions triển khai trên Firebase yêu cầu gói Blaze; emulator chạy được trước
khi bật thanh toán. Xem tài liệu chính thức:
https://firebase.google.com/docs/functions/get-started

Không đặt service-account JSON vào repo, web hoặc APK. Không thay quy tắc hiện tại
bằng allow-all để vượt lỗi quyền. Rules trong thư mục Firebase thay toàn bộ bộ rules
hiện tại khi deploy: phải đối chiếu collection ngoài phạm vi trước khi áp dụng.

```powershell
cd D:\TTTN\Firebase
npx firebase-tools deploy --project PROJECT_ID --only functions:dispatch,firestore,storage
```

Sau triển khai, web `VITE_FIREBASE_EMULATORS=false`; Android dựng/cài lại **không có**
`-PfirebaseEmulators=true`. Tài khoản thật và emulator là hai môi trường khác nhau;
nên xóa dữ liệu app khi đổi môi trường để không mang hàng đợi thử nghiệm sang môi trường thật.

## Giới hạn phải ghi vào khóa luận

- Chưa chạy nghiệm thu nghiệp vụ, tranh chấp đồng thời và rules trên emulator/máy chủ.
- Một doanh nghiệp, tối đa 20 đơn/chuyến, 30 mặt hàng/đơn, 100 lần đổi người/chuyến.
- Chưa có hóa đơn/cước phí, phân bổ một đơn cho nhiều xe, chuyến quay đầu hay tối ưu tuyến.
- Chưa có tải ảnh/biên nhận giao hàng, bản đồ tuyến/GPS trực tiếp, push notification,
  tra cứu giấy phép chính thức hoặc tự đối chiếu pháp lý loại xe. Đã có hồ sơ tài xế,
  tệp giấy tờ và quản lý xác nhận xe được phân công; xem `driver-profiles.md`.
- Giờ sẵn sàng/hạn giao là yêu cầu trên đơn, chưa có ETA tự động/kiểm tra tất cả khung giờ
  điểm dừng. Không tự hoàn thành bằng GPS.
- Xe/tài xế đang chạy bị chặn phân công thêm cho đến khi kết thúc/bàn giao;
  đây là ràng buộc thận trọng cho phạm vi demo, chưa là bộ xếp lịch ca hoàn chỉnh.
- Web đọc realtime toàn collection cho dữ liệu demo; cần phân trang/lọc và lưu trữ lịch sử
  trước khi tăng quy mô. Chưa xác thực tài xế bằng khuôn mặt.
- Điểm mắt vẫn là quy tắc thử nghiệm, chưa được hiệu chỉnh bằng tập tài xế độc lập.

## Hồ sơ và tài khoản bổ sung

Xem [Hồ sơ tài xế, tài khoản và form đơn](driver-profiles.md). Storage Emulator
chạy cùng máy chủ local; bản APK mới có màn hình nộp giấy tờ và lưu nháp riêng.
