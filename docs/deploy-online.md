# Chạy thật: Firebase + Render

## Kiến trúc được chọn

```text
Web trên Firebase Hosting ───┬── Firebase Auth: tài khoản thật
Android online ──────────────┼── Firestore: dữ liệu dùng chung, realtime
                            ├── Supabase Storage Private: giấy tờ (qua Render)
                            └── Render Node API: quyền và nghiệp vụ điều phối
```

Project: `driverguard-854e5`. Không nhập dữ liệu emulator, tài khoản demo hay hồ sơ giả
vào project thật. Dữ liệu cloud đang có được giữ nguyên. Ảnh mới dùng Supabase, không
cần Firebase Storage/Blaze cho luồng này. Render Free có thời gian ngủ; yêu cầu đầu
tiên có thể chờ khoảng một phút. Server không lưu giấy tờ trên đĩa Render.

Web app Firebase đã đăng ký: `1:925387921691:web:6f90e598e95e44b0039bbe`.
Cấu hình SDK công khai được cập nhật trong `Web/src/api/firebase.ts`; đây không phải
khóa riêng của service account.

## 1. Chuẩn bị Firebase thật

Trong Firebase Console, chọn đúng project:

- Authentication: bật Email/Password. Các email đã đăng ký tiếp tục dùng đúng mật khẩu
  của Firebase, không phải mật khẩu demo. Quên mật khẩu dùng chức năng đặt lại qua email.
- Firestore: dùng database `(default)` đã có.
- Không cần bật Firebase Storage. Auth và Firestore vẫn dùng Firebase thật.
- Project settings → Service accounts → Generate new private key: lưu ngoài Git.
  Nội dung JSON này chỉ điền vào **Environment của backend Render** hoặc terminal quản trị.
  Không đưa vào Web, APK, ảnh chụp, chat hay repository.
- Authentication → Settings → Authorized domains: thêm tên miền web thực tế.
  Nếu mở web tại máy phát triển và dùng luồng cần authorized domain, thêm `localhost`.
- Nếu Android dùng Google Sign-In: đăng ký SHA-1/SHA-256 của bản ký thực tế và tải
  `google-services.json` mới. Email/Password không cần cấu hình Google Sign-In.

## 2. Tạo backend Render

Đưa thay đổi code lên nhánh mà Render lấy mã trước. File `render.yaml` hỗ trợ Blueprint;
hoặc tạo **New → Web Service** với cấu hình sau:

| Mục | Giá trị |
|---|---|
| Language / Runtime | Node |
| Root Directory | `Firebase/functions` |
| Build Command | `npm ci --omit=dev` |
| Start Command | `npm start` |
| Health Check Path | `/health` |
| Instance | Free nếu dùng demo khóa luận |

Environment:

```dotenv
NODE_VERSION=22
FIREBASE_PROJECT_ID=driverguard-854e5
FIREBASE_STORAGE_BUCKET=driverguard-854e5.firebasestorage.app
ALLOWED_ORIGINS=https://driverguard-854e5.web.app,https://driverguard-854e5.firebaseapp.com,http://localhost:5173,http://127.0.0.1:5173
FIREBASE_SERVICE_ACCOUNT_JSON=<toàn bộ JSON khóa riêng, điền dưới dạng secret>
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<khóa máy chủ Supabase, điền dưới dạng secret>
SUPABASE_DOCUMENT_BUCKET=driver-documents
```

`ALLOWED_ORIGINS` chứa chính xác origin của **giao diện**, không phải URL backend;
không thêm dấu `/` cuối. Nếu web chạy trên Render Static Site thì thêm origin đó.
Không đặt bất kỳ biến emulator nào trên Render. Server sẽ từ chối khởi động nếu thấy chúng.

Service account Firebase cần quyền đọc/ghi Firestore, đọc tài khoản Auth và kiểm tra
phiên bị thu hồi. Nếu gặp lỗi IAM, cấp đúng quyền trong project; không chuyển
Firestore/Storage Rules sang public để chữa lỗi quyền server.

Sau triển khai, lấy URL Render thực tế, ví dụ `https://<ten-api>.onrender.com`.
`/health` cho biết tiến trình đang chạy; chưa chứng minh toàn bộ nghiệp vụ đã chạy thành công.

## 3. Cấu hình web và Android bằng cùng URL

Trong PowerShell tại thư mục dự án:

```powershell
.\Configure-Online.ps1 -ApiUrl https://<ten-api>.onrender.com
```

Lệnh tạo `Web/.env.production.local` và `Mobile/DriverGuard/online.properties`.
Hai file được bỏ qua trong Git, không chứa khóa máy chủ.

Nếu bộ emulator đang chiếm cổng 5173, dừng bằng `Stop-Local.ps1` để xuất dữ liệu rồi:

```powershell
.\Start-Online.ps1
```

Giao diện có nhãn **Firebase thật**. Mặc dù URL giao diện là localhost, tài khoản và dữ liệu
được đọc từ Firebase cloud. Đây không phải bộ emulator. Không dùng `Start-Local.ps1`
hoặc APK có nhãn `DriverGuard Local` khi muốn đồng bộ tài khoản thật.

## 4. Triển khai Rules

Đọc lại thay đổi Rules trước khi triển khai vì chúng áp dụng cho các client đang dùng
project thật. Quyền nhân viên lấy từ `staff/{uid}`, quyền tài xế từ `drivers/{uid}`;
không dùng đuôi email để đoán vai trò.

```powershell
cd Firebase
node node_modules/firebase-tools/lib/bin/firebase.js login
node node_modules/firebase-tools/lib/bin/firebase.js deploy --only firestore --project driverguard-854e5
```

Không cần triển khai Firebase Storage/Functions cho kiến trúc này. Storage Rules cũ
chỉ phục vụ tệp Firebase cũ hoặc emulator.

## 5. Cấp quản trị đầu tiên

Nếu Firestore thật đã có quản trị đang hoạt động trong `staff/{uid}`, dùng tài khoản đó.
Nếu chưa có, chọn tài khoản Auth thật thuộc người quản lý và xác minh email. Trong
terminal tin cậy tại `Firebase/functions`, cấu hình credentials như `.env.example`, rồi:

```powershell
node --env-file=.env scripts/bootstrap-admin.js FIREBASE_UID "Tên quản lý" NV001
```

Script kiểm tra UID tồn tại, email đã xác minh và chưa có admin; không tạo tài khoản,
không thay mật khẩu, không tự cấp quyền theo email. Những nhân viên tiếp theo được
admin cấp quyền trong web. Hồ sơ tài xế vẫn cần nộp và duyệt riêng.

## 6. Tạo kho giấy tờ Supabase riêng tư

Tạo project Supabase Free. Lấy Project URL và khóa server tại Project settings/API keys.
Điền `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_DOCUMENT_BUCKET` trên
Render. Khóa server chỉ nằm trong môi trường backend; không đưa vào web, APK hoặc Git.
Không dùng anon/publishable key thay cho khóa server.

Tạo bucket tên `driver-documents`, **Private**, tối đa 5 MB/tệp, chỉ nhận JPG/PNG/PDF.
Không thêm policy cho phép anon/public hoặc mọi người dùng đọc/ghi bucket. App đăng
nhập bằng Firebase, backend kiểm tra token rồi mới dùng khóa server truy cập Supabase.

Có thể tạo/cấu hình bucket bằng script, sau khi điền Supabase env trong `.env` tại
`Firebase/functions` (file bỏ qua Git):

```powershell
node --env-file=.env scripts/setup-document-storage.js
```

Web tải blob qua `GET /driver-documents?path=...` với Firebase ID token. Android
upload raw bytes qua `POST /driver-documents?kind=...`. Backend chỉ cho tài xế pending/
active upload cho chính mình; chỉ chủ hồ sơ hoặc admin đang active được đọc. Không
phát URL public hay lưu ảnh dưới dạng base64 trong Firestore.

Tệp mới có khóa `supabase:driver-documents/{uid}/{kind}/{uuid}` trong hồ sơ; đây là
khóa định danh, không phải URL công khai. Tệp Firebase cũ không tự chuyển sang Supabase.
Nếu bucket cũ không truy cập được vì chưa có Blaze, tài xế chọn tải lại ảnh sang kho mới;
bỏ tệp tùy chọn cũ khỏi bản nháp nếu không dùng. Hồ sơ nộp lại vẫn cần quản lý duyệt lại.

Chỉ bắt buộc hai mặt GPLX. Ba tệp căn cước/sức khỏe là tùy chọn. Các thông tin/hạn
giấy tờ vẫn cần khai báo, quản lý đối chiếu theo quy trình nội bộ; không tự duyệt chỉ
vì upload thành công. Chưa có tác vụ tự dọn tệp bỏ nháp; quản lý cần chính sách lưu/xóa
phù hợp trước khi mở rộng dùng hồ sơ cá nhân thực.

## 7. Xuất web và APK thật

Tại thư mục gốc:

```powershell
.\Deploy-OnlineWeb.ps1
.\Build-OnlineAndroid.ps1
```

Web được build lại với URL Render vừa cấu hình rồi triển khai lên Firebase Hosting.
APK nằm tại `Mobile/DriverGuard/app/build/outputs/online/DriverGuard-online.apk`.
Đây là APK debug phục vụ cài trực tiếp, chưa phải bản release ký để phát hành cửa hàng.
Ứng dụng online dùng package `com.example.driverguard`; bản Local dùng package riêng.

Nếu chọn Render Static Site thay cho Firebase Hosting: Root Directory `Web`, Build
`npm ci && npm run build`, Publish `dist`; đặt `VITE_FIREBASE_EMULATORS=false`,
`VITE_FIREBASE_PROJECT_ID=driverguard-854e5`, `VITE_DISPATCH_API_URL` và bucket trong
Render Environment. Thêm domain đó vào các cấu hình origin ở trên.

## Trạng thái triển khai

Ngày 20/10/2026: backend `https://driverguard-1.onrender.com/health` phản hồi online;
giao diện đã triển khai tại `https://driverguard-854e5.web.app` và trả đúng bundle mới.
Render cho phép CORS từ origin giao diện này. APK online đã dựng với URL backend thật.
Đây là xác nhận HTTP/triển khai, chưa nghiệm thu đăng nhập, phân quyền hay upload giấy tờ.
Đường dẫn `/` của backend là API và trả NOT_FOUND; người dùng mở URL Firebase Hosting.
Script deploy tạo bundle trong `Firebase/hosting-dist` vì CLI yêu cầu tệp nằm trong
thư mục project Firebase; thư mục generated này được bỏ qua trong Git.

Code hỗ trợ chạy thật không có nghĩa là dịch vụ đã được triển khai. Phải có URL Render,
secret credentials trên Render, Supabase Private, Firestore Rules và quyền nhân viên thật. Chưa có các thông
tin đó thì không tạo APK có URL giả và không tuyên bố hệ thống online đã hoạt động.

Tài liệu chính thức:

- [Firebase callable protocol](https://firebase.google.com/docs/functions/callable-reference)
- [Firebase Hosting](https://firebase.google.com/docs/hosting)
- [Supabase Private buckets](https://supabase.com/docs/guides/storage/buckets/fundamentals)
- [Supabase Storage permissions](https://supabase.com/docs/guides/storage/security/access-control)
- [Render Node service](https://render.com/docs/deploy-node-express-app)
- [Render Free limits](https://render.com/docs/free)

## Lỗi Render: Missing script: start

Ảnh lỗi ngày 20/10 cho thấy Render đang chạy commit `e009f80`. File
`Firebase/functions/package.json` của commit đó chưa có `scripts.start`. Bản sửa
hiện có `"start": "node server.js"` và server mới nhưng chưa nằm trong commit đó.

Cần đưa đầy đủ bản sửa (bao gồm lockfile, `server.js`, `runtime.js`,
`document-storage.js`, `dossiers.js`, `quantities.js`) lên nhánh Render đang lấy mã,
kiểm tra Root Directory là `Firebase/functions`, sau đó Manual Deploy → Deploy latest commit.
Không chạy `npm start` trong `Web` vì thư mục đó là giao diện Vite.
