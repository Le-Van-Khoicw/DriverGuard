from fastapi import FastAPI
from app.api.v1 import auth, users, devices, device_bindings, vehicles, monitoring_sessions, drowsiness_events, detection_settings, device_health

app = FastAPI(title="Driver Drowsiness Admin API")

app.include_router(auth.router, prefix="/api/v1")
app.include_router(users.router, prefix="/api/v1")
app.include_router(devices.router, prefix="/api/v1")
app.include_router(device_bindings.router, prefix="/api/v1")
app.include_router(vehicles.router, prefix="/api/v1")
app.include_router(monitoring_sessions.router, prefix="/api/v1")
app.include_router(drowsiness_events.router, prefix="/api/v1")
app.include_router(detection_settings.router, prefix="/api/v1")
app.include_router(device_health.router, prefix="/api/v1")