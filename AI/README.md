# Đánh giá AI DriverGuard theo video

Mục tiêu: đo **trạng thái mắt** và **cảnh báo nhắm mắt kéo dài** của baseline Android.
Đây là thử nghiệm pilot; nhắm mắt có chủ đích không chứng minh buồn ngủ sinh lý.

**Phạm vi phiên bản:** công cụ này giữ baseline nhận diện ban đầu, trước phần lưu
chuyến và thang điểm `observation-rules-v1`. Báo cáo đã xuất ghi hash mã/model tại
thời điểm chạy. App mới yêu cầu đủ mẫu hiệu chỉnh, kiểm tra camera ngừng gửi mẫu,
và đo thống kê theo thời gian thực; không dùng kết quả baseline này để khẳng định
đã kiểm chứng điểm chuyến mới. Xem `docs/trip-scoring.md` và tests Android cho phần đó.

```text
Video ──> MediaPipe của app ──> EAR ──> ngưỡng + bộ đếm ──> dự đoán
                                                              ↓
Nhãn do người xem video quyết định ───────────────────────> đối chiếu
                                                              ↓
                                         TP / FP / TN / FN + coverage
```

## Dữ liệu hiện có

`dataset.csv` trỏ tới bốn video và nhãn `frame,label` trong đồ án Thị giác máy tính cũ.
Chạy với `--data-root` để chọn thư mục dữ liệu; không sửa hay di chuyển bản gốc.
Cả bốn video hiện gắn `P01`, theo thông tin người cung cấp. Split train/validation giữ
theo README của dữ liệu cũ. Chưa có người hoặc phiên test độc lập.

Nhãn cũ được tái sử dụng, **chưa được xem lại thủ công trong lần tích hợp này**.
Không dùng kết quả của notebook cũ làm kết quả Android: notebook có Haar crop,
còn app đưa toàn ảnh vào MediaPipe. Tên video không được dùng làm nhãn.

## Chạy trên máy hiện tại

Máy có Python 3.12, MediaPipe 0.10.35 và OpenCV 4.13.0.92.
Mở PowerShell tại `D:\TTTN`:

```powershell
# Chỉ cần cài nếu môi trường chưa có các thư viện.
py -3.12 -m pip install -r AI/requirements.txt

py -3.12 -X utf8 AI/evaluate.py `
  --data-root "D:\it\ThiGiacMayTinh\project\data" `
  --output AI/outputs/my_run_01
```

Mỗi lần chạy chọn tên output mới để giữ lịch sử thực nghiệm. `--help` xem tùy chọn.
Không phải huấn luyện: pipeline dùng model có sẵn của app và replay luật Kotlin.

Sau khi lần chạy hoàn tất, xuất biểu đồ để đọc lỗi theo thời gian:

```powershell
py -3.12 -X utf8 AI/plot_report.py --run AI/outputs/my_run_01
```

Mỗi phiên có thêm `timeline.png`, gồm EAR, nhãn mắt, dự đoán và các lần phát cảnh báo.
Vùng xám là hiệu chỉnh; vùng xanh ở biểu đồ cuối là khoảng cần cảnh báo theo nhãn.
Mũi tên FP có thể là cảnh báo sớm, không nhất thiết xảy ra lúc mắt mở.

Đầu ra:

- `report.md`: bảng kết quả từng phiên, giới hạn và định nghĩa chỉ số.
- `report.json`: chi tiết confusion matrix, coverage, cấu hình, hash model/video/nhãn/code.
- `summary.csv`: bảng để mở bằng Excel; train/validation luôn có cột riêng.
- `<người>_<phiên>/frames.csv`: EAR, thời gian video, nhãn thật, dự đoán, cảnh báo từng frame.
- `<người>_<phiên>/closure_events.csv`: từng đoạn nhắm kéo dài, có bắt được và trễ bao lâu.
- `<người>_<phiên>/warning_events.csv`: từng lần cảnh báo, ghép đúng hay sai, có bị sớm không.
- `COMPLETE`: chỉ xuất hiện khi toàn bộ manifest xử lý xong. Thiếu file này là lần chạy chưa hoàn tất.

## Đang replay điều gì?

Các giá trị mặc định lấy từ mã Kotlin hiện tại:

1. 5 giây đầu lấy mẫu EAR > 0.15; ngưỡng = EAR trung bình × 0.75.
2. Cửa sổ quá khứ 13 frame; tối thiểu 70% mẫu hợp lệ.
3. Dùng EAR hiện tại đã được nội suy để phân loại OPEN/CLOSED.
4. CLOSED liên tục 2 giây thì bật trạng thái cảnh báo.
5. Ghi sự kiện cảnh báo ở cạnh bật, với cooldown > 5 giây như ViewModel.
   Còi của MonitoringScreen bật theo trạng thái DROWSY, không bị gate ghi log này chặn.
   `alarm_onset` trong CSV là ghi log; `sound_onset` mới là cạnh bật trạng thái còi.

Người quay phải mở mắt tự nhiên, nhìn rõ camera trong giai đoạn hiệu chỉnh.
Nếu không có mẫu, baseline dùng 0.25 giống Kotlin; báo cáo ghi rõ fallback.
Một số mẫu nhắm mắt vẫn có EAR > 0.15 và có thể làm hỏng hiệu chỉnh: phải kiểm tra
5 giây đầu khi đọc kết quả. Dùng ngưỡng cố định khi mắt mở không đúng yêu cầu sẽ
đổi bài toán, không nên âm thầm sửa ngưỡng sau khi đã thấy kết quả test.

**Hành vi cần lưu ý của app:** TemporalFeatureExtractor nội suy cả mẫu thiếu ở
cuối cửa sổ. Vì vậy mất khuôn mặt vài frame chưa chắc chuyển UNKNOWN ngay.
Replay giữ nguyên hành vi này để đo hiện trạng, chưa sửa thuật toán Android.

Dùng cùng model `.task`, landmarks chuẩn hóa và `RunningMode.IMAGE` của app.
Không thay đổi tỷ lệ ảnh trong cách tính EAR vì Kotlin đang dùng tọa độ chuẩn hóa.
Python có thể khác Kotlin do số thực và nhịp xử lý. Đây là đo offline toàn frame,
không phải đo FPS/độ trễ xử lý thực trên điện thoại. Thời gian dùng PTS của video,
không lấy số frame chia FPS để đo closure trên clip có tốc độ khung hình thay đổi.

## Cách đọc kết quả

**Eye:** positive = CLOSED, negative = OPEN. Confusion matrix chỉ chấm khi nhãn
và dự đoán đều OPEN/CLOSED; phải đọc cùng coverage và số UNKNOWN. Trường
`recall_counting_unknown_as_miss` tính thêm các frame CLOSED mà hệ thống không
phân loại được thành bỏ sót, tránh giấu mất dữ liệu bằng cách chỉ báo F1 cao.

**Alarm timeline:** positive khi nhãn CLOSED liên tục đủ 2 giây, negative trước
đó hoặc khi OPEN. So với `alarm_active`, có đủ TP/FP/TN/FN theo frame. Chớp mắt
ngắn không yêu cầu còi hú. Frame calibration/nhãn UNKNOWN/chưa gán bị loại.
Đoạn CLOSED đã bắt đầu trước monitoring được tính lại từ khi bắt đầu monitoring.

**Alarm events:** mỗi đoạn CLOSED liên tục đủ 2 giây là một sự kiện cần phát hiện.
Một cảnh báo được ghép với tối đa một sự kiện, trong khoảng từ thời điểm đủ điều
kiện đến trước khi mở mắt. Không có dung sai ghép sớm: cảnh báo EARLY_WARNING
dù đang CLOSED vẫn tính FP và sự kiện đó có thể FN. Cảnh báo thêm là FP;
không có cạnh bật cảnh báo là FN. Một cảnh báo kéo dài qua nhiều đoạn nhắm do
không nhận ra lúc mở mắt không được tính là phát hiện mới nhiều lần. Không có TN theo sự kiện vì
không chia thời gian bình thường thành một số sự kiện tùy ý.

Nhãn UNKNOWN hoặc khoảng chưa gán nhãn ngắt chuỗi nhắm và không được chấm.
Không tự xem thiếu nhãn là mắt mở. Precision/recall không có mẫu để tính sẽ là
`null`/`N/A`, không phải 0 hoặc 100%. Báo nhầm/giờ từ video ngắn chỉ là chuẩn hóa.

## Thêm hai người sau

Giữ nguyên ngưỡng trước khi đánh giá người mới. Với phiên test, 5 giây hiệu chỉnh
mắt mở theo đúng quy trình app vẫn được phép; không dùng nhãn test để chỉnh ngưỡng.

1. Đặt video/nhãn trong `AI/data/raw/` và `AI/data/labels/`, hoặc thư mục riêng.
2. Mỗi người/phiên có ID khác nhau, ví dụ P02/session01. Không chia ngẫu nhiên
   frame liền nhau thành train và test.
3. Gán nhãn với công cụ bên dưới, rồi thêm dòng vào manifest hoặc tạo manifest mới.
4. Nếu video nằm trong thư mục khác, có thể dùng đường dẫn tuyệt đối ở cột video/labels.
5. Chạy lại với output mới. Có thể báo cáo riêng các dòng `test`; không gộp với train.

Ví dụ dòng mới (thay tên file bằng file thật):

```csv
participant,session,split,video,labels
P02,session01,test,raw/videos/p02_session01.mp4,labels/p02_session01.csv
P03,session01,test,raw/videos/p03_session01.mp4,labels/p03_session01.csv
```

## Gán nhãn video mới hoặc xem lại nhãn cũ

```powershell
py -3.12 -X utf8 AI/annotate.py `
  --video "AI/data/raw/p02_session01.mp4" `
  --output "AI/data/labels/p02_session01.csv"
```

- O: mắt mở; C: mắt nhắm; U: không đủ hình để quyết định, gồm mất mặt/che mắt.
- Space: lặp nhãn frame trước; B: lùi để sửa; Q/Esc: lưu và thoát.
- Tự lưu mỗi 50 frame và khi đóng cửa sổ. Chạy lại tiếp tục ở frame chưa gán.
- `--start-frame 0` để xem lại từ đầu. Sửa nhãn cũ nên xuất ra bản sao để giữ nguồn.
- Không có dự đoán EAR trên màn hình gán nhãn để tránh người gán bị dẫn dắt.

## Kiểm tra công cụ

```powershell
py -3.12 -X utf8 -m unittest discover -s AI -p "test_*.py" -v
```

Các ca kiểm tra tập trung vào nhắm kéo dài bị bỏ sót, chớp mắt ngắn, UNKNOWN,
ghép cảnh báo một lần và hành vi nội suy frame thiếu. Chúng không thay thế đánh
giá video thật. Xem định nghĩa chính thức của
[MediaPipe Python Face Landmarker](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker/python)
và [confusion matrix](https://scikit-learn.org/stable/modules/generated/sklearn.metrics.confusion_matrix.html).
