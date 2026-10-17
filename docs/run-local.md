# Chạy bản khóa luận trên máy, không dùng Blaze

Máy tính chạy Firebase Emulator (Auth, Firestore, Functions, Storage) và web. Android xử lý
camera trên điện thoại và gửi nghiệp vụ về máy tính. Dùng project giả lập
`demo-driverguard`; không cần nhập thẻ thanh toán hay deploy lên Firebase thật.

```text
Web trình duyệt ───────────┐
                          ├→ Máy tính: Auth + Firestore + dispatchCommand
Android Local ────────────┘
       └→ Camera / EAR / cảnh báo và lưu offline trên thiết bị
```

## 1. Lần đầu cài thư viện

Máy đã có Node và Java 21. Từ PowerShell:

```powershell
cd D:\TTTN\Firebase
npm ci
cd functions
npm ci
cd ..\..\Web
npm ci
```

Lần khởi động đầu Firebase CLI tải emulator nên cần internet để tải công cụ.
Sau khi có đủ công cụ và thư viện, luồng demo nội bộ không cần dịch vụ Firebase thật.

## 2. Khởi động

```powershell
cd D:\TTTN
.\Start-Local.ps1
```

Giữ terminal đó mở. Khi tải emulator xong, script tạo dữ liệu local còn thiếu rồi
khởi động web. Dữ liệu đã có không bị seed ghi đè.

- Web: http://127.0.0.1:5173
- Quản lý emulator: http://127.0.0.1:4000

| Vai trò | Email local | Mật khẩu ban đầu |
|---|---|---|
| Quản lý | admin@driverguard.test | Demo123456! |
| Điều phối | dieuphoi@driverguard.test | Demo123456! |
| Tài xế Android | taixe@driverguard.test | Demo123456! |

Đây là tài khoản minh họa chỉ tồn tại trong emulator, không đăng nhập tài khoản
Google thật. Đã có một xe demo tải trọng 5.000 kg, chưa có chuyến giả hoàn thành.

## 3. Android Emulator

Mở Android Emulator bằng Android Studio, sau đó trong terminal khác:

```powershell
cd D:\TTTN
.\Build-LocalAndroid.ps1 -Install
```

Mở **DriverGuard Local**, đăng nhập tài khoản tài xế ở bảng trên. Nếu nhiều thiết bị
cùng kết nối, thêm `-Device emulator-5554` (thay bằng serial thật từ `adb devices`).

Script dựng bản `com.example.driverguard.local`, cài cạnh app hiện tại, dùng dữ liệu
và hàng đợi riêng. APK lưu ở:
`Mobile/DriverGuard/app/build/outputs/local/DriverGuard-local.apk`.

## 4. Điện thoại Android thật qua USB

Bật USB debugging, kết nối cáp với máy tính và chấp nhận kết nối gỡ lỗi trên điện thoại:

```powershell
cd D:\TTTN
.\Build-LocalAndroid.ps1 -PhoneUsb -Install
```

Chế độ này dùng `adb reverse` cho cổng 9099, 8080, 5001 và 9199. Điện thoại truy cập server
qua USB, không cần đưa emulator ra internet hoặc cấu hình IP LAN. Cần mở lại các cổng
reverse sau khi rút/cắm lại cáp. APK cho Android Emulator và USB phone dùng địa chỉ
server khác nhau; dựng lại đúng chế độ thiết bị.

Khi rút cáp/tắt máy chủ: giám sát mắt và lưu tiến độ đang thực hiện vẫn trên máy;
nhận/bắt đầu chuyến cần kết nối lại. Web chỉ cập nhật khi máy chủ đang chạy.

## 5. Đi thử nghiệp vụ

0. Tài xế mở **Hồ sơ nghề nghiệp**, nhập thông tin và nộp các tệp minh họa.
   Admin vào **Tài xế → bấm tên → Hồ sơ tài xế**, đối chiếu, chọn xe và duyệt.
   Tài khoản chưa active cần cấp mã nhân viên/kích hoạt sau khi duyệt hồ sơ.
1. Web đăng nhập quản lý hoặc điều phối, tạo đơn có liên hệ lấy/giao hàng, hàng và kg.
2. Lập chuyến, chọn xe `DEMO-001` và tài xế `TX001`, đặt giờ kết thúc còn trong tương lai.
3. Android vào tab **Chuyến**, nhận rồi bắt đầu chuyến.
4. Mở giám sát từ chi tiết chuyến để báo cáo mắt gắn đúng phân công.
5. Quay về Chuyến (camera sẽ tạm dừng), ghi từng điểm nhận/giao rồi hoàn thành.
6. Web xem kết quả và báo cáo quan sát. Điểm có thể chưa đủ dữ liệu; không mặc định 100.

Đây là hướng dẫn thao tác; chưa thay thế kiểm thử nghiệp vụ, quyền và tranh chấp đồng thời.

## 6. Dừng và giữ dữ liệu

Nhấn **Ctrl+C một lần** ở terminal khởi động, chờ thông báo xuất dữ liệu hoàn tất.
Nếu server mở từ Codex hoặc không có terminal đó, chạy `D:\TTTN\Stop-Local.ps1` trong PowerShell.
Script xuất Auth/Firestore/Storage vào `Firebase/.emulator-data`, lần khởi động sau tự import.
Không đóng cưỡng bức terminal nếu muốn giữ thay đổi cuối cùng. Thư mục này bị Git bỏ qua.

Nếu một cổng đang bị chương trình khác dùng, script báo lỗi; không tự tắt chương trình
khác. Không chạy hai bộ máy chủ local cùng lúc. Khi khởi động lỗi, giữ log để xem nguyên nhân.

Chi tiết phân quyền tài khoản và hồ sơ mới: [Hồ sơ tài xế](driver-profiles.md).
