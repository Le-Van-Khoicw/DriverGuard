from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.v1 import (
    auth, users, devices, device_bindings, vehicles,
    monitoring_sessions, drowsiness_events,
    detection_settings, device_health,
    dashboard, global_search, reports, audit_logs,
    locations,
)
app = FastAPI(title="Driver Drowsiness Admin API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://localhost:5173",
    ],  # sửa lại đúng port thật FE đang dùng
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api/v1")
app.include_router(users.router, prefix="/api/v1")
app.include_router(devices.router, prefix="/api/v1")
app.include_router(device_bindings.router, prefix="/api/v1")
app.include_router(vehicles.router, prefix="/api/v1")
app.include_router(monitoring_sessions.router, prefix="/api/v1")
app.include_router(drowsiness_events.router, prefix="/api/v1")
app.include_router(detection_settings.router, prefix="/api/v1")
app.include_router(device_health.router, prefix="/api/v1")
app.include_router(dashboard.router, prefix="/api/v1")
app.include_router(global_search.router, prefix="/api/v1")
app.include_router(reports.router, prefix="/api/v1")
app.include_router(audit_logs.router, prefix="/api/v1")
app.include_router(locations.router, prefix="/api/v1")