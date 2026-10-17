# Form vận chuyển, tài khoản và hồ sơ tài xế

## 1. Form đơn gọn theo mô hình vận tải thuê

`Web/src/app/TransportOrderForm.tsx` chia thông tin thành khách thuê, điểm lấy,
điểm giao, hàng hóa/thời gian. Có ví dụ chữ mờ; nhóm lấy hàng có lựa chọn dùng tên
và số liên hệ bên khách. Nếu người bàn giao là thủ kho khác, bỏ chọn và nhập liên hệ.
Không đổi khóa `sender` của dữ liệu cũ; giao diện dùng tên “Người liên hệ lấy hàng”.

## 2. Phân biệt tài khoản

- Firebase Auth xác định UID, email, cách đăng nhập và trạng thái xác minh email.
- `staff/{uid}` cấp quyền admin/dispatcher; `drivers/{uid}` là hồ sơ tài xế.
- Không tự cấp quyền theo đuôi Gmail, tên email hoặc nút đăng ký đã bấm.
- Một UID có thể có cả hai vai trò nếu quản lý cấp, nhưng phải xét từng quyền riêng.
- Trang **Tài khoản đăng ký** chỉ dành cho admin, lấy Auth qua callable
  `listCompanyAccounts`, 50 tài khoản/trang. Hiển thị provider Google/Email-password,
  email đã xác minh, tình trạng Auth, hồ sơ/vai trò và thời gian đăng nhập.
- Trang này chỉ đọc môi trường hiện tại. Tài khoản Firebase thật và project local
  `demo-driverguard` tách riêng. Không tự nhập tài khoản/mật khẩu thật vào emulator.
- Nhân viên có **Hồ sơ của tôi** để sửa tên và số liên hệ; không tự đổi vai trò,
  email đăng nhập hoặc mã nhân viên.

## 3. Hồ sơ tài xế

Thông tin: họ tên theo giấy tờ, ngày sinh, điện thoại, địa chỉ, căn cước, số/hạng GPLX,
ngày cấp/hết hạn, ngày khám/hạn hồ sơ sức khỏe, người và số liên hệ khẩn cấp.

Trong phạm vi demo, bộ hồ sơ nội bộ cần 5 tệp: căn cước hai mặt, GPLX hai mặt,
giấy khám/xác nhận sức khỏe. JPG/PNG/PDF, mỗi tệp tối đa 5 MB. Đây là **quy trình
hồ sơ nội bộ được chọn cho đồ án**, không phải danh mục mọi giấy tờ pháp luật yêu
cầu tài xế mang theo khi đi đường. Tài liệu phương tiện thuộc hồ sơ xe công ty,
không yêu cầu tài xế tải lại vào hồ sơ cá nhân. Đề tài chỉ chở hàng thông thường.

Điều kiện lái xe có yêu cầu về tuổi, sức khỏe và GPLX phù hợp, còn hiệu lực/còn điểm;
app hiện không tra cứu được các điều kiện đó từ cơ quan cấp giấy. Tham khảo nguồn
chính thức: [Điều kiện và giấy tờ của người lái xe](https://xaydungchinhsach.chinhphu.vn/khi-tham-gia-giao-thong-duong-bo-nguoi-lai-xe-phai-mang-theo-cac-giay-to-nao-119241228075421372.htm),
[Thông tư 36/2024/TT-BYT về sức khỏe người lái xe](https://vanban.chinhphu.vn/?classid=0&docid=211769&pageid=27160).

Không tự mặc định hạn sức khỏe là một số tháng cố định. Ngày được khai theo tài liệu
và quy trình công ty, quản lý phải đối chiếu trước khi duyệt. Không suy hạng GPLX
từ khối lượng hàng (kg); quản lý đối chiếu giấy tờ xe thực tế và quy định tương ứng.

```text
Đăng ký / xác minh email
        ↓
Hồ sơ nghề nghiệp → nhập thông tin → tải 5 tệp → nộp hồ sơ
        ↓
Admin mở hồ sơ → xem tệp → đối chiếu → chọn xe được phép phân công
        ├→ Yêu cầu bổ sung, có lý do → tài xế sửa và nộp lại
        └→ Duyệt hồ sơ → cấp mã nhân viên / kích hoạt tài khoản
                                          ↓
                           Điều phối mới giao được chuyến phù hợp
```

Người chưa được kích hoạt có nút **Hoàn thiện hồ sơ và nộp giấy tờ** tại màn hình
chờ duyệt. Người đã vào app: **Cài đặt → Hồ sơ → Hồ sơ nghề nghiệp và giấy tờ tài xế**.
App lưu nháp riêng theo UID; chỉ coi đã nộp khi máy chủ xác nhận. Tải tệp/nộp cần mạng.
Nháp giấy tờ không được đưa vào Android Auto Backup/Device Transfer.

## 4. Duyệt và kiểm tra phân công

- `accountStatus` và `qualificationStatus` độc lập: tài khoản hoạt động không tự
  chứng minh hồ sơ đã được duyệt.
- Admin chọn các xe đã xác nhận phù hợp hồ sơ khi duyệt, lưu `approvedVehicleIds`.
- Phân công, nhận, bắt đầu và bàn giao kiểm tra hồ sơ được duyệt, GPLX/hồ sơ sức khỏe
  còn hạn đến cuối chuyến và xe nằm trong danh sách quản lý xác nhận.
- Tài xế nộp bản mới làm hồ sơ chuyển lại chờ duyệt; không âm thầm giữ phê duyệt cũ.
- Cập nhật hồ sơ không chặn tài xế ghi kết quả/kết thúc chuyến đang thực hiện.
- Duyệt yêu cầu đúng revision; bản mới nộp không thể bị một màn hình cũ duyệt nhầm.
- Mỗi lần nộp lưu bản trong `driverDossiers/{uid}/revisions/{revision}` cùng người/thời
  điểm/ghi chú đối chiếu, không chuyển giấy tờ cũ sang người khác.

## 5. Quyền truy cập

- `driverDossiers/{uid}` và revisions: chỉ tài xế chủ hồ sơ và admin đọc; client
  không ghi trực tiếp, mọi lần nộp/duyệt đi qua máy chủ.
- `drivers/{uid}` chỉ chứa tóm tắt phục vụ điều phối: trạng thái, hạng, hạn và xe được
  xác nhận. Điều phối không được đọc ảnh căn cước hoặc hồ sơ sức khỏe chi tiết.
- Tệp ở `driver-documents/{uid}/{kind}/{uuid}`, Storage Rules giới hạn chủ hồ sơ/admin.
  Đường dẫn chỉ là khóa lưu trữ; web lấy blob có xác thực, không hiển thị download URL công khai.
- Tệp bất biến sau upload; thay tệp tạo đối tượng mới và yêu cầu nộp/duyệt lại.
- Ghi chú duyệt hồ sơ riêng tư không sao chép vào audit chung mà dispatcher đọc được.
- Chưa có tác vụ dọn tệp upload dở/bỏ nháp hoặc chính sách lưu trữ/xóa hồ sơ nghỉ việc.
  Cần bổ sung trước khi dùng dữ liệu cá nhân thực ở môi trường triển khai thật.

## 6. Local và trạng thái hoàn thành

Storage Emulator mới chạy cổng 9199; script start/stop tự giữ cùng Auth/Firestore.
USB phone cần dựng/cài lại qua `Build-LocalAndroid.ps1 -PhoneUsb -Install` để có
thêm `adb reverse tcp:9199 tcp:9199`. Android Emulator dùng bản APK Local mới.

Tài xế demo cũ chưa có giấy tờ và không được tự đánh dấu đã duyệt. Vì vậy cần nộp
bộ tài liệu minh họa rồi quản lý duyệt trước khi giao chuyến mới. Không dùng giấy tờ
cá nhân thật chỉ để lấp dữ liệu demo.

Đã biên dịch Web/Android và khởi động emulator có Storage. Chưa kiểm thử tự động
hoặc nghiệm thu đầy đủ việc tải tệp, quyền truy cập, xung đột và luồng nộp → duyệt.
