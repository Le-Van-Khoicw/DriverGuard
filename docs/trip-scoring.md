# Chuyến đi và điểm đánh giá chạy trên điện thoại

## Luồng sử dụng

```text
Bắt đầu chuyến → Hiệu chỉnh mắt 5 giây → Giám sát
                                           ├─ Tạm dừng → Tiếp tục → Hiệu chỉnh lại
                                           └─ Hoàn thành → Xác nhận
                                                               ↓
                                             Tính báo cáo ngay trên máy
                                                               ↓
                                         Lưu báo cáo + xóa chuyến dở cùng lúc
                                                               ↓
                                          Đồng bộ Firestore khi có internet
```

- GPS không tự đóng chuyến; tài xế chọn hoàn thành khi đã dừng xe.
- Tạm dừng không tạo báo cáo cuối và không tạo ID chuyến mới khi tiếp tục.
- Rời màn hình giám sát sẽ tạm dừng. Quay lại chọn tiếp tục hoặc hoàn thành.
- PiP vẫn là cùng màn hình và tiếp tục giám sát theo cơ chế camera hiện có.
- Một ViewModel dùng chung ở cấp activity tránh nhiều bản chuyến khi đổi tab.
- Khởi động lại app khôi phục chuyến dở ở trạng thái **tạm dừng**; camera không tự bật.

## Lưu trên máy và đồng bộ

`TripLocalStore` dùng SharedPreferences với transaction JSON riêng theo UID.
Ghi đĩa bằng `commit()` trên IO, tuần tự qua Mutex. Checkpoint khoảng mỗi 2 giây,
và khi tạm dừng/có sự kiện. Khi tiến trình bị tắt đột ngột có thể mất dữ liệu sau
checkpoint gần nhất; không coi khoảng ứng dụng không chạy là thời gian đã quan sát.

Hoàn thành chỉ báo thành công sau khi lưu báo cáo và xóa draft trong cùng lần ghi.
Nếu lỗi bộ nhớ, giữ chuyến dở và cho thử lại. Báo cáo không phụ thuộc API AI hay GPS.

- ID chuyến là UUID cố định từ lúc bắt đầu; gửi lại cùng ID không tạo chuyến trùng.
- Revision ngăn checkpoint cũ ghi đè checkpoint mới. Báo cáo đã hoàn thành ngăn
  checkpoint ghi muộn làm chuyến dở xuất hiện lại.
- Báo cáo mới có cờ chờ đồng bộ bền vững. Firestore SDK xử lý ghi khi mất mạng;
  hàng đợi cục bộ được gửi lại khi đăng nhập/mở app nếu lần trước chưa gửi xong.
- Chỉ bỏ cờ chờ sau khi máy chủ xác nhận và lưu được xác nhận đó trên máy.
- Lỗi đồng bộ không làm mất báo cáo; có nút thử đồng bộ trong báo cáo.
- Đăng xuất/đổi tài khoản dừng chuyến hiện tại và đổi bộ dữ liệu hiển thị theo UID.

## Thống kê quan sát

`TripObservationAccumulator` nhận timestamp monotonic, EAR và trạng thái mắt.
Nó đo thời gian theo các khoảng giữa mẫu, độc lập với số log cảnh báo.

- Khoảng chỉ hợp lệ nếu hai đầu có EAR hữu hạn, trạng thái OPEN/CLOSED và cách
  nhau không quá 500 ms. Mất mẫu/UNKNOWN ngắt chuỗi nhắm liên tục.
- Đếm riêng thời gian hợp lệ, thời gian mắt nhắm, chuỗi nhắm dài nhất và số
  đoạn nhắm từ 2 giây. Khi chuỗi vượt 2 giây, tính cả phần 2 giây đầu.
- EAR trung bình có trọng số thời gian, lấy từ toàn bộ quan sát hợp lệ.
- Tạm dừng, hiệu chỉnh, mất hình và khoảng app bị đóng không cộng thời gian hợp lệ.
- Độ bao phủ = thời gian hợp lệ / toàn bộ thời gian chuyến, **bao gồm cả tạm dừng**
  trong mẫu số. Đây là lựa chọn bảo thủ vì app không biết tài xế làm gì khi tắt giám sát.
- Tỷ lệ mắt nhắm = thời gian mắt nhắm / thời gian hợp lệ. UI không gọi con số này
  là PERCLOS vì chưa áp dụng đầy đủ định nghĩa/đánh giá PERCLOS chuẩn.

## Điểm `observation-rules-v1`

Đây là **thang điểm thử nghiệm do dự án quy định**, chưa được huấn luyện hoặc
kiểm chứng là mức độ an toàn thực. TP/FP/TN/FN đo bộ phát hiện, không phải nhãn
đúng/sai cho điểm chuyến. Các trọng số dưới đây cần được hiệu chỉnh bằng dữ liệu
và đánh giá độc lập nếu muốn đưa ra kết luận rộng hơn.

Chỉ chấm nếu đồng thời:

1. Ít nhất **30 giây quan sát mắt hợp lệ**.
2. Độ bao phủ toàn chuyến ít nhất **70%**.

Thiếu một trong hai điều kiện: điểm `null`, UI hiện **Chưa đủ dữ liệu**. Vẫn lưu
thống kê và sự kiện; tuyệt đối không biến thiếu dữ liệu thành điểm 100.

```text
r = tổng thời gian các đoạn nhắm từ 2s / thời gian hợp lệ
f = số đoạn nhắm từ 2s / số giờ quan sát hợp lệ
l = lần nhắm dài nhất (giây)

điểm = round(100 - min(50, 200*r) - min(30, f) - min(20, 2*l))
giới hạn điểm: 0..100
```

Ví dụ: chuyến 40 giây, quan sát hợp lệ 35 giây, không có đoạn nhắm kéo dài được
100 điểm theo quy tắc này. Chuyến 100 giây nhưng chỉ quan sát được 35 giây không
được chấm. Cùng một sự kiện trên thời gian quan sát dài hơn có tần suất thấp hơn.

Hạng A ≥90, B ≥70, C ≥40, D <40. Hạng là cách trình bày thang điểm, không phải
chẩn đoán sức khỏe hoặc xác nhận chuyến xe an toàn. Không phạt theo ngưỡng demo
60 giây, không suy ra mệt mỏi chỉ từ thời gian chạy, không dùng GPS thiếu làm tốc độ 0 để chấm.

Báo cáo cũ vẫn đọc được, nhưng không trình bày điểm cũ như đã qua kiểm tra chất
lượng quan sát. Dữ liệu cũ trên Firestore không bị ghi lại hay xóa khi mở báo cáo.

## Kiểm tra

Từ `Mobile/DriverGuard`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --console=plain
```

Unit tests kiểm tra camera mất mẫu, ngắt chuỗi, tích lũy/khôi phục thống kê, điểm
thiếu dữ liệu, chuẩn hóa theo thời gian và tương thích schema. Tests Android
kiểm tra ghi file thật, khôi phục checkpoint kèm sự kiện/GPS, thứ tự revision,
hoàn thành không trùng, checkpoint ghi muộn, pending sync và tách tài khoản.
Tests lưu trữ không ghi dữ liệu giả lên Firebase.

Kịch bản thử thủ công tiếp theo trên điện thoại:

1. Khi ngồi yên, bắt đầu và để camera thấy mắt tự nhiên khoảng 40–60 giây.
2. Tạm dừng, đổi tab, quay lại: vẫn cùng chuyến, có nút tiếp tục/hoàn thành.
3. Tắt cả Wi-Fi và dữ liệu di động, hoàn thành: có báo cáo ngay và trạng thái chờ đồng bộ.
4. Đóng/mở lại app: báo cáo còn trong lịch sử. Bật mạng lại: kiểm tra cùng một ID trên web/Firestore.
5. Đóng app giữa chuyến: mở lại phải khôi phục checkpoint ở trạng thái tạm dừng.
6. Che camera phần lớn thời gian hoặc hoàn thành quá sớm: phải hiện chưa đủ dữ liệu.

Các test tự động chưa thay thế việc kiểm tra camera, PiP, mất mạng/khôi phục kết
nối và Firestore Rules thật trên điện thoại. Sai số nhận diện mắt vẫn ảnh hưởng điểm;
đánh giá video trong `AI/` là bước kiểm tra riêng, chưa phải kiểm chứng thang điểm v1.
