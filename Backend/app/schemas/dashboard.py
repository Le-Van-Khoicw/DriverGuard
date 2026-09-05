from pydantic import BaseModel


class DashboardSummary(BaseModel):
    totalDevices: int
    onlineDevices: int
    offlineDevices: int
    sessionsToday: int
    alertsToday: int
    unhandledAlerts: int


class AlertTrendPoint(BaseModel):
    date: str
    count: int