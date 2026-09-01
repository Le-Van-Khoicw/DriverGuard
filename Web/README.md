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
