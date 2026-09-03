const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8000/api/v1";

export type User = {
  id: string;
  username: string | null;
  phone: string | null;
  fullName: string;
  role: "admin" | "driver";
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
};

export type Device = {
  id: string;
  deviceCode: string;
  deviceName: string;
  deviceType: string;
  status: "online" | "offline" | "locked";
  firmwareVersion: string | null;
  aiModelVersion: string | null;
  lastSeenAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type Vehicle = {
  id: string;
  userId: string;
  displayName: string;
  licensePlate: string | null;
  vehicleType: "motorbike" | "car" | "truck" | "bus" | null;
  createdAt: string;
  updatedAt: string;
};

export type MonitoringSession = {
  id: string;
  userId: string;
  deviceId: string;
  vehicleId: string | null;
  status: "active" | "ended";
  startedAt: string;
  endedAt: string | null;
};

export type DrowsinessEvent = {
  id: string;
  sessionId: string;
  eventType: string;
  ear: number | null;
  confidence: number | null;
  closedDurationMs: number | null;
  imageUrl: string | null;
  occurredAt: string;
  status: "NEW" | "ACKNOWLEDGED" | "RESOLVED";
  handledBy: string | null;
  note: string | null;
};

export type DashboardSummary = {
  totalDevices: number;
  onlineDevices: number;
  offlineDevices: number;
  sessionsToday: number;
  alertsToday: number;
  unhandledAlerts: number;
};

export type AlertTrendPoint = { date: string; count: number };

export type EventPage = {
  items: DrowsinessEvent[];
  total: number;
  page: number;
  pageSize: number;
};

class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

function token() {
  return sessionStorage.getItem("driverguard_token");
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const accessToken = token();
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (response.status === 401) {
    sessionStorage.removeItem("driverguard_token");
    window.dispatchEvent(new Event("driverguard:unauthorized"));
  }
  if (!response.ok) {
    let message = "Không thể kết nối tới máy chủ";
    try {
      const body = await response.json();
      message = body.detail || message;
    } catch {
      // Response is not JSON.
    }
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  const value = search.toString();
  return value ? `?${value}` : "";
}

export const api = {
  async login(username: string, password: string) {
    const form = new URLSearchParams({ username, password });
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: form,
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new ApiError(body.detail || "Tên đăng nhập hoặc mật khẩu không đúng", response.status);
    }
    const data = await response.json();
    sessionStorage.setItem("driverguard_token", data.access_token);
  },
  logout() {
    sessionStorage.removeItem("driverguard_token");
  },
  hasToken: () => Boolean(token()),

  dashboard: () => request<DashboardSummary>("/dashboard/summary"),
  alertTrend: (days = 7) => request<AlertTrendPoint[]>(`/dashboard/alert-trend?days=${days}`),
  users: () => request<User[]>("/users?role=driver"),
  createUser: (data: { full_name: string; phone?: string }) =>
    request<User>("/users", { method: "POST", body: JSON.stringify({ ...data, role: "driver", is_active: true }) }),
  updateUser: (id: string, data: Record<string, unknown>) =>
    request<User>(`/users/${id}`, { method: "PATCH", body: JSON.stringify(data) }),

  devices: () => request<Device[]>("/devices"),
  createDevice: (data: { deviceCode: string; deviceName: string; deviceType: string }) =>
    request<Device>("/devices", { method: "POST", body: JSON.stringify(data) }),
  updateDevice: (id: string, data: { deviceName?: string; status?: string }) =>
    request<Device>(`/devices/${id}`, { method: "PATCH", body: JSON.stringify(data) }),

  vehicles: () => request<Vehicle[]>("/vehicles"),
  createVehicle: (data: Omit<Vehicle, "id" | "createdAt" | "updatedAt">) =>
    request<Vehicle>("/vehicles", { method: "POST", body: JSON.stringify(data) }),
  updateVehicle: (id: string, data: Partial<Vehicle>) =>
    request<Vehicle>(`/vehicles/${id}`, { method: "PATCH", body: JSON.stringify(data) }),
  deleteVehicle: (id: string) => request<void>(`/vehicles/${id}`, { method: "DELETE" }),

  sessions: (params: Record<string, string> = {}) =>
    request<MonitoringSession[]>(`/monitoring-sessions${query(params)}`),
  events: (params: Record<string, string | number | undefined> = {}) =>
    request<EventPage>(`/drowsiness-events${query({ pageSize: 100, ...params })}`),
  updateEventStatus: (id: string, status: DrowsinessEvent["status"], note?: string) =>
    request<DrowsinessEvent>(`/drowsiness-events/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, note: note || null }),
    }),
  exportReportUrl: () => `${API_BASE_URL}/reports/export`,
};
