# 🛡️ DriverGuard — Real-time Driver Drowsiness Detection & Fleet Monitoring System

> **Hệ thống giám sát và cảnh báo buồn ngủ cho tài xế theo thời gian thực (Edge AI On-device & Web Quản trị đội xe)**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![MediaPipe](https://img.shields.io/badge/Google_MediaPipe-Face_Mesh-FF6F00?logo=google&logoColor=white)](https://developers.google.com/mediapipe)
[![Firebase](https://img.shields.io/badge/Firebase-Firestore_%26_Auth-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com/)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![Leaflet](https://img.shields.io/badge/Leaflet-GPS_Map-199900?logo=leaflet&logoColor=white)](https://leafletjs.com/)

---

## 🔗 Liên kết nhanh

* 📱 **Tải ứng dụng Android (APK):** [Google Drive Download](https://drive.google.com/drive/folders/1gif8OSaaAdV5PEcPah4ZGLM4oasmezzl?usp=sharing)
* 🌐 **Web Quản trị Trung tâm (Live Demo):** [DriverGuard Web Admin trên Render](https://driverguard-ni7q.onrender.com)
  * *Tài khoản quản trị demo:* `admin@gmail.com` / `admin123`

---

## 📌 Tổng quan đề tài

Tai nạn giao thông do ngủ gật và mất tập trung khi lái xe là một trong những nguyên nhân hàng đầu gây thiệt hại nghiêm trọng về người và tài sản. Hầu hết các giải pháp hiện nay hoặc yêu cầu **phần cứng đắt tiền** (hệ thống DMS chuyên dụng từ 5–15 triệu VNĐ), hoặc phụ thuộc vào việc **stream video lên máy chủ** gây trễ lớn và vi phạm quyền riêng tư tài xế.

**DriverGuard** giải quyết triệt để bài toán này bằng mô hình **Edge AI On-device**:
1. Sử dụng chính **smartphone thông thường** gắn trên cabin xe làm thiết bị giám sát thông minh.
2. Xử lý nhận diện khuôn mặt và nháy mắt trực tiếp trên chip điện thoại (0ms độ trễ, không cần internet để phát hiện).
3. Tự động phát âm thanh cảnh báo tức thì khi tài xế có dấu hiệu ngủ gật, đồng thời lưu bằng chứng (ảnh chụp, tọa độ GPS) và đồng bộ thời gian thực lên Cloud về trung tâm điều hành.

---

## ✨ Tính năng nổi bật

### 1. 👁️ Giám sát On-Device Edge AI (Mobile App)
* **Google MediaPipe Face Mesh:** Trích xuất 468 điểm mốc (landmarks) khuôn mặt theo thời gian thực với hiệu năng tối ưu bằng CameraX.
* **Thuật toán EAR (Eye Aspect Ratio):** Tính toán tỷ lệ mở mắt dựa trên khoảng cách giữa các điểm mí mắt trên và dưới.
* **Bộ lọc chống báo động giả (State Machine):** Chỉ kích hoạt báo động khi $EAR < 0.22$ kéo dài liên tục trên **2.0 giây**, phân biệt chính xác giữa nháy mắt sinh lý bình thường và buồn ngủ thật sự.
* **Cơ chế Cooldown (5 giây):** Chống spam âm thanh và ngăn bắn lặp hàng trăm thông báo làm treo thiết bị.

### 2. ⚡ Đồng bộ Realtime & Bằng chứng sự kiện (Cloud Sync)
* Tự động **chụp ảnh khuôn mặt** thời điểm tài xế nhắm mắt để làm bằng chứng cho doanh nghiệp vận tải.
* Lấy **tọa độ GPS** và tốc độ di chuyển tại thời điểm xảy ra sự kiện.
* Đồng bộ 2 chiều tức thì với **Firebase Firestore** và quản lý tài xế qua **Firebase Authentication**.

### 3. 🗺️ Web Dashboard Quản trị Đội xe (Fleet Center)
* **Bản đồ trực quan:** Tích hợp Leaflet với bản đồ Google Maps tiếng Việt chuẩn chủ quyền biển đảo Việt Nam (Hoàng Sa, Trường Sa, Biển Đông).
* **Smooth FlyTo & Auto-focus:** Nhấp vào bất kỳ sự kiện hoặc xe nào trên bảng dữ liệu, bản đồ sẽ tự động lướt mượt (`flyTo`) và phóng to chi tiết vị trí xe.
* **Quản lý toàn diện:** Theo dõi danh sách tài xế, phương tiện, thiết bị, lịch sử sự kiện buồn ngủ và nhật ký lộ trình di chuyển.

---

## 🏗️ Kiến trúc hệ thống (System Architecture)

```
┌─────────────────────────────────────────────────────────────┐
│                    TÀI XẾ TRÊN XE (CABIN)                   │
│                                                             │
│   [ Camera trước ] ──> [ CameraX ImageAnalysis (30 FPS) ]   │
│                                 │                           │
│                                 ▼                           │
│                     [ MediaPipe Face Mesh ]                 │
│                                 │                           │
│                                 ▼                           │
│                    [ Thuật toán tính EAR ]                  │
│                                 │                           │
│        ┌────────────────────────┴───────────────────────┐   │
│        ▼ EAR >= 0.22                                    ▼   │
│   [ Bình thường ]                                EAR < 0.22 │
│                                                (Liên tục 2s)│
│                                                         │   │
│                                     ┌───────────────────┴─┐ │
│                                     ▼                     ▼ │
│                              [ Hú còi báo động ]    [ Chụp ảnh ]
│                              [ Rung điện thoại ]    [ Lấy GPS ]
└─────────────────────────────────────────────────────────┬───┘
                                                          │
                                     Đồng bộ Realtime     ▼
┌─────────────────────────────────────────────────────────────┐
│                   FIREBASE CLOUD INFRASTRUCTURE             │
│                                                             │
│    • Firebase Auth: Xác thực tài xế & Quản trị viên         │
│    • Cloud Firestore: Lưu trữ Alerts, Locations, Trips, Users│
└──────────────────────────────────────────┬──────────────────┘
                                           │
                                           │ Realtime Listener
                                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  WEB ADMIN DASHBOARD (RENDER)               │
│                                                             │
│    • Theo dõi vị trí xe trực tiếp trên Google Maps (Leaflet)│
│    • Xem lại ảnh bằng chứng buồn ngủ & chỉ số EAR           │
│    • Quản trị tài xế, phương tiện & xem biểu đồ xu hướng    │
└─────────────────────────────────────────────────────────────┘
```

---

## 💻 Công nghệ sử dụng (Tech Stack)

| Phân hệ | Công nghệ & Thư viện | Vai trò |
|---|---|---|
| **Mobile App** | Kotlin, Jetpack Compose, Material 3 | Giao diện hiện đại, Reactive UI |
| | Android CameraX | Thu nhận luồng hình ảnh hiệu năng cao |
| | Google MediaPipe Tasks Vision | Mô hình AI Face Mesh chạy On-Device |
| | Fused Location Provider | Định vị GPS thời gian thực |
| | Firebase Android SDK | Đồng bộ Firestore & Xác thực Auth |
| **Web Admin** | React 18, Vite, TypeScript | SPA Dashboard quản trị hiệu năng cao |
| | Leaflet, React-Leaflet | Hiển thị bản đồ định vị xe |
| | Firebase Web SDK (v11) | Nhận dữ liệu Realtime không cần reload |
| | Lucide React, Recharts | Icon hệ thống & Biểu đồ thống kê |
| **Backend (Optional)** | Python FastAPI, SQLAlchemy, MySQL | Cổng API mở rộng cho hệ thống On-premise |

---

## 📂 Cấu trúc thư mục dự án

```
DriverGuard/
├── Mobile/
│   └── DriverGuard/             # Mã nguồn Android App (Jetpack Compose + AI)
│       └── app/src/main/java/com/example/driverguard/
│           ├── core/            # Theme, Color tokens, Utilities
│           ├── data/            # Firestore Repositories, Location Service
│           └── feature/
│               ├── auth/        # Đăng nhập, đăng ký tài xế
│               ├── monitoring/  # CameraX, FaceMesh EAR, Hú còi cảnh báo
│               ├── history/     # Xem lại lịch sử các lần buồn ngủ
│               └── settings/    # Tùy chỉnh ngưỡng EAR, thời gian báo động
├── Web/                         # Mã nguồn Web Admin Dashboard
│   ├── src/
│   │   ├── api/                 # Firebase SDK client & Firestore listeners
│   │   ├── app/                 # Dashboard, LocationPanel (Map), AlertsPage
│   │   └── styles/              # Dark/Light theme styles
├── Backend/                     # API Server FastAPI (Tùy chọn kết nối MySQL)
└── docs/                        # Tài liệu đặc tả, sơ đồ kiến trúc
```

---

## 🚀 Hướng dẫn cài đặt & Khởi chạy

### 1. Chạy Mobile App (Android)
1. Cài đặt **Android Studio** (Hedgehog hoặc mới hơn).
2. Mở thư mục `Mobile/DriverGuard`.
3. Đảm bảo file `google-services.json` đã đặt đúng vị trí `Mobile/DriverGuard/app/google-services.json`.
4. Kết nối điện thoại thật qua USB Debugging (khuyên dùng điện thoại thật để test camera & GPS).
5. Nhấn **Run** (Shift + F10) hoặc cài trực tiếp file `.apk` từ liên kết tải phía trên.

### 2. Chạy Web Admin Dashboard
Yêu cầu: **Node.js 18+**
```bash
# Di chuyển vào thư mục Web
cd Web

# Cài đặt dependencies
npm install

# Chạy ở môi trường Development
npm run dev
# Mở trình duyệt tại: http://localhost:5173

# Đóng gói sản phẩm (Production build)
npm run build
```

---

## 👥 Tác giả & Đề tài

* **Sinh viên thực hiện:** Lê Văn Khởi
* **Đề tài:** Hệ thống giám sát an toàn tài xế và cảnh báo buồn ngủ thời gian thực (DriverGuard)
* **Trường:** Đại học Giao thông Vận tải TP.HCM (UTH)