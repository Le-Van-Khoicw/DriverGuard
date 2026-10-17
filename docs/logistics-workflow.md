# Nghiệp vụ mục tiêu: vận tải hàng hóa liên tỉnh và giám sát tài xế

Trạng thái: yêu cầu nghiệp vụ mục tiêu. Đã bắt đầu triển khai web, Android và máy
chủ Firebase; xem `logistics-implementation.md` để biết phần đã viết, cách chạy và
giới hạn. Chưa triển khai/ nghiệm thu xuyên suốt; tài liệu này không phải quy trình
pháp lý hay khẳng định đã khảo sát một doanh nghiệp cụ thể.

## 1. Phạm vi và người sử dụng

Đơn vị thử nghiệm là một doanh nghiệp/đội xe tải hàng hóa liên tỉnh.

| Vai trò | Quyền và trách nhiệm |
|---|---|
| Quản trị/quản lý | Cấp quyền nhân viên; quản lý tài xế, xe; duyệt nghỉ; xem báo cáo; có thể làm công việc điều phối |
| Điều phối viên | Tạo/xác nhận đơn vận chuyển; lập chuyến; giao hoặc thay tài xế; sắp thứ tự điểm dừng; xử lý giao hàng có vấn đề |
| Tài xế | Xem chuyến được phân công; nhận/từ chối có lý do; thực hiện các điểm nhận/giao; báo sự cố, xin nghỉ; hoàn thành chuyến |

Mỗi chuyến lưu người tạo và người phân công bằng ID nhân viên. Không dùng tên
nhập tự do làm khóa liên kết tài xế, xe hay nhân viên điều phối.

Trạng thái tài khoản (chờ duyệt/hoạt động/khóa) độc lập với trạng thái nhân sự
(sẵn sàng/đang có chuyến/nghỉ). Người đang nghỉ vẫn cần mở app để xem thông tin.

## 2. Ba đối tượng phải tách riêng

- **Đơn vận chuyển:** yêu cầu chở hàng cho khách, có thông tin hàng, nơi nhận,
  người gửi, nơi giao, người nhận và thời gian yêu cầu.
- **Chuyến:** kế hoạch thực hiện bằng một xe với người điều phối và tài xế được
  phân công. Một chuyến chở một hoặc nhiều đơn.
- **Điểm dừng:** một lần nhận/giao hàng theo thứ tự của chuyến. Một chuyến có một
  hoặc nhiều điểm; mỗi điểm liên kết rõ những đơn cần xử lý tại đó.

Phạm vi đầu tiên: mỗi đơn có một nơi lấy hàng và một nơi giao cho một người nhận;
đơn được xếp nguyên vào một chuyến tại một thời điểm. Một chuyến có thể gom nhiều
đơn để giao nhiều địa chỉ. Lịch sử lần thực hiện cũ được giữ khi điều phối giao lại.

```text
Khách hàng → Đơn vận chuyển A ─┐
Khách hàng → Đơn vận chuyển B ─┼→ Chuyến T001 → Xe + tài xế + điều phối
Khách hàng → Đơn vận chuyển C ─┘       ↓
                            Điểm 1: nhận hàng
                            Điểm 2: giao đơn A
                            Điểm 3: giao đơn B, C
                                     ↓
                           Kết quả từng đơn + báo cáo chuyến
```

## 3. Dữ liệu cần có

### Đơn vận chuyển

- Mã đơn; khách đặt vận chuyển, người liên hệ và số điện thoại.
- Người gửi; địa chỉ nhận hàng và tọa độ nếu có.
- Người nhận hàng, số điện thoại; địa chỉ giao và tọa độ nếu có.
- Danh sách hàng: tên/mô tả, số lượng, đơn vị tính; trọng lượng kg, thể tích nếu biết.
- Thời gian sẵn sàng lấy hàng; hạn/khung giờ giao mong muốn.
- Ghi chú xử lý hàng, trạng thái và người tạo/xác nhận đơn.
- Khi giao: thời gian thực tế, người nhận thực tế, số lượng đã giao, ghi chú,
  ảnh/biên nhận khi có; lý do giao thiếu/không thành công.

Khách đặt vận chuyển có thể khác người gửi và khác người nhận hàng.

### Chuyến

- Mã chuyến, tên/mô tả; danh sách đơn; người tạo, điều phối viên phụ trách.
- Xe bằng ID và biển số hiển thị; tài xế hiện tại bằng ID và mã nhân viên.
- Kế hoạch khởi hành/kết thúc; thời gian bắt đầu/kết thúc thực tế.
- Danh sách điểm dừng có thứ tự; giờ dự kiến và kết quả thực tế từng điểm.
- Trạng thái; phiên bản phân công; lịch sử đổi tài xế/xe và lý do.
- Vị trí gần nhất kèm thời gian cập nhật và trạng thái đồng bộ.

### Xe và nhân sự

- Xe: biển số, loại xe, tải trọng kg, khả năng chở hàng theo hồ sơ công ty,
  trạng thái sẵn sàng/đang làm chuyến/bảo trì/ngừng sử dụng.
- Nhân viên: mã nhân viên, tài khoản, vai trò, liên hệ, lịch làm việc/phân công.
- Đơn nghỉ: nhân viên, từ thời điểm nào đến thời điểm nào, lý do, trạng thái duyệt,
  người duyệt và thời điểm duyệt.
- Ràng buộc phù hợp giữa tài xế và loại xe dựa trên hồ sơ do đơn vị xác nhận;
  phần mềm không tự xác nhận các điều kiện pháp lý của hồ sơ.

## 4. Phân công và nghỉ

```text
Điều phối chọn chuyến + xe + tài xế
                   ↓
Kiểm tra tài khoản / nghỉ đã duyệt / lịch trùng / xe / tải trọng
                   ↓ hợp lệ
Phân công có ghi người giao, thời điểm, phiên bản
                   ↓
Tài xế nhận hoặc từ chối có lý do
                   ↓
Bắt đầu chuyến → thực hiện điểm nhận/giao → hoàn thành
```

Kiểm tra lại ràng buộc khi xác nhận phân công và khi bắt đầu chuyến, không chỉ
lọc dropdown. Lịch dự kiến dùng để giữ chỗ; chuyến đang chạy chưa kết thúc vẫn
giữ tài xế/xe, kể cả đã quá giờ kết thúc dự kiến. Hai điều phối viên thao tác đồng
thời không được phân cùng tài xế/xe vào các chuyến trùng nhau.

### Nghỉ trước khi khởi hành

1. Tài xế gửi thời gian nghỉ cụ thể. Đơn chờ duyệt tạo cảnh báo cho người xếp lịch.
2. Khi quản lý duyệt, hiển thị những chuyến bị ảnh hưởng. Chúng chuyển sang cần
   phân công lại; người đang nghỉ không được bắt đầu các chuyến trùng khoảng nghỉ.
3. Điều phối chọn người thay từ danh sách đủ điều kiện và còn lịch trống.
4. Nếu không có người thay, chuyến hiển thị chờ xử lý; điều phối đổi lịch/hủy có lý do.
5. Phân công mới tăng phiên bản, thông báo cả người cũ/người mới và yêu cầu người
   mới nhận chuyến. Không âm thầm chọn một nhân viên bất kỳ.

### Bàn giao sau khi chuyến đã chạy

- Tạo yêu cầu bàn giao, ghi người cũ/người mới, xe, vị trí, thời điểm, lý do,
  tình trạng hàng và các điểm đã xử lý/còn lại.
- Điều phối xác nhận; kết thúc phiên lái của người cũ và mở phiên mới cho người
  nhận. Nếu thiết bị cũ không sử dụng được, quản lý xử lý ngoại lệ có ghi rõ lý do.
- Giữ nguyên mã chuyến và kết quả giao hàng trước đó. Không đổi toàn bộ lịch sử
  cảnh báo sang tên người mới.
- Bàn giao quyền trên hệ thống cần xác nhận từ máy chủ. Khi mất mạng, lưu thông
  tin sự cố trên máy để đồng bộ; không tự khẳng định đã chuyển quyền điều khiển.
- Bản cập nhật từ thiết bị giữ phiên bản phân công cũ phải được đối chiếu khi
  đồng bộ. Dữ liệu quan sát lịch sử vẫn giữ theo người/phiên; lệnh đổi trạng thái
  chuyến lỗi thời không được ghi đè phân công mới.

## 5. Theo dõi và hoàn thành

Trạng thái chuyến đề xuất:

```text
Nháp → Đã phân công → Đã nhận → Đang thực hiện → Hoàn thành
             ↓             ↓                        hoặc
       Cần phân công lại / Hoãn             Kết thúc có vấn đề
```

Hủy trước khi bắt đầu cần lý do và giải phóng tài xế/xe/đơn khỏi lịch đang giữ.
Chuyến đang chạy xử lý bằng sự cố/bàn giao/kết thúc có vấn đề, có vết lịch sử.

Trạng thái điểm: chưa đến → đã đến → đang xử lý → hoàn tất hoặc không thành công.
Tài xế cập nhật kết quả nhận/giao từng đơn ở điểm đó. Giao đủ, giao thiếu và giao
không thành công là những kết quả khác nhau, kèm số lượng và lý do tương ứng.

Chỉ hoàn thành thành công khi tất cả nhiệm vụ nhận/giao đã được xác nhận đầy đủ.
Nếu có đơn chưa giao đủ, cho kết thúc có vấn đề và chuyển điều phối xử lý; không
biến cả chuyến thành đã giao thành công. Chuyến có một điểm cũng dùng cùng quy trình.

GPS có thể gợi ý đã tới gần điểm; thao tác nghiệp vụ nhận/giao và hoàn thành cần
xác nhận của tài xế khi đã dừng xe. GPS gần địa chỉ không chứng minh đã giao hàng.

## 6. Gắn giám sát AI vào người và chuyến

```text
Chuyến → Lịch sử phân công / phiên lái → Phiên giám sát → Cảnh báo + thống kê
```

Mỗi bản ghi cần liên kết `tripId`, `driverId` và `monitoringSessionId`; phiên bản
phân công được ghi cùng các thao tác cần kiểm tra quyền. Tài khoản khai báo người
lái không đồng nghĩa hệ thống đã xác thực danh tính bằng khuôn mặt.

- Điểm quan sát tính theo từng tài xế/phiên lái, với điều kiện đủ dữ liệu.
- Báo cáo chuyến tổng hợp các phiên và độ bao phủ, vẫn cho xem ai lái ở giai đoạn nào.
- Không gán lỗi nhận diện/nhắm mắt của tài xế cũ cho người mới sau bàn giao.
- Báo cáo kết quả giao hàng độc lập với báo cáo quan sát mắt. Giao đủ hàng không
  tự làm điểm quan sát tốt; thiếu hình không tự biến đơn thành giao thất bại.
- Cảnh báo và lưu trên điện thoại tiếp tục khi mất internet. Web hiển thị thời
  điểm dữ liệu cuối cùng nhận được; điểm chưa có dữ liệu không được giả thành 100.

## 7. Các tình huống phải dùng để nghiệm thu

1. Điều phối giao chuyến A cho tài xế B và xe C; B nhận, bắt đầu, nhận/giao hàng,
   hoàn thành; web thấy kết quả từng đơn và báo cáo quan sát.
2. Một chuyến một điểm giao và một chuyến nhiều điểm giao theo thứ tự.
3. Tài xế nghỉ đã duyệt, lịch tài xế trùng, xe đang có chuyến/bảo trì hoặc quá tải:
   hệ thống chặn phân công không hợp lệ; có người thay phù hợp hoặc trạng thái chờ xử lý.
4. Hai điều phối cùng phân công tài xế/xe: chỉ thao tác hợp lệ được xác nhận.
5. Đổi tài xế trước khi chạy: người cũ không còn quyền bắt đầu bằng phân công cũ.
6. Bàn giao giữa chuyến: giữ lịch sử, chia đúng người/phiên, không nhập điểm AI hai người thành một.
7. Một điểm giao thiếu/không thành công: giữ kết quả thật, có lý do và điều phối xử lý.
8. Mất internet khi thực hiện; mở lại app; đồng bộ lại không nhân đôi kết quả và
   không ghi đè phân công đã đổi trong lúc thiết bị mất mạng.

## 8. Thứ tự triển khai

1. Thống nhất schema Firebase và quyền quản trị/điều phối/tài xế, bỏ đăng nhập demo.
2. Hồ sơ tài xế/xe, nghỉ và kiểm tra lịch trước khi giao chuyến.
3. Đơn vận chuyển, lập chuyến, nhiều điểm dừng, phân công và nhận chuyến.
4. Thực hiện nhận/giao, xử lý ngoại lệ và đổi tài xế có lịch sử.
5. Liên kết giám sát hiện có với chuyến/phiên lái, lưu offline và giải quyết xung đột.
6. Báo cáo web và kiểm thử xuyên suốt theo các tình huống ở trên.

Tài liệu SQL cũ `erd.dbml` và `api-contract.md` mô tả kiến trúc cũ; code Web/Android
hiện truy cập Firebase. Cần thống nhất nguồn dữ liệu này trước khi thêm các bảng/
collection để tránh một bên ghi MySQL trong khi bên kia đọc Firestore.

Tham khảo nghiệp vụ nhiều điểm: tài liệu chính thức ERPNext Delivery Trip mô tả
một chuyến theo xe, tài xế và nhiều điểm giao theo khách hàng:
https://docs.frappe.io/erpnext/delivery-trip
