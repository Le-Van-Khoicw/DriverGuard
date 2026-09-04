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

export type RecentAlert = Pick<DrowsinessEvent, "id" | "sessionId" | "eventType" | "occurredAt" | "status">;
export type DeviceBinding = { id: string; userId: string; deviceId: string; status: "active" | "ended"; boundAt: string; unboundAt: string | null };
export type DetectionSetting = { id: string; deviceId: string | null; earThreshold: number; confidenceThreshold: number; closedDurationThresholdMs: number; updatedAt: string };
export type DeviceHealth = { id: string; deviceId: string; status: string; lastHeartbeatAt: string | null; note: string | null; createdAt: string };
export type AuditLog = { id: string; adminId: string; action: string; targetTable: string; targetId: string | null; beforeValue: Record<string, unknown> | null; afterValue: Record<string, unknown> | null; createdAt: string };
export type SearchResult = { type: "user" | "device" | "vehicle"; id: string; title: string; subtitle: string | null };

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

async function fetchResponse(url: string, init: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(url, init);
  } catch {
    throw new ApiError("Không thể kết nối tới máy chủ. Vui lòng thử lại.", 0);
  }
  if (response.status === 401) {
    sessionStorage.removeItem("driverguard_token");
    window.dispatchEvent(new Event("driverguard:unauthorized"));
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const detail = body.detail;
    const message = typeof detail === "string" ? detail : Array.isArray(detail)
      ? detail.map((item: { msg?: string }) => item.msg || "Dữ liệu không hợp lệ").join("; ")
      : `Yêu cầu thất bại (HTTP ${response.status})`;
    throw new ApiError(message, response.status);
  }
  return response;
}

async function request<T>(path: string, init: RequestInit = {}, format: "json" | "blob" = "json"): Promise<T> {
  const headers = new Headers(init.headers);
  const accessToken = token();
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");

  const response = await fetchResponse(`${API_BASE_URL}${path}`, { ...init, headers });
  if (response.status === 204) return undefined as T;
  if (format === "blob") return response.blob() as Promise<T>;
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
    const response = await fetchResponse(`${API_BASE_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: form,
    });
    const data = await response.json();
    if (typeof data.access_token !== "string" || !data.access_token.trim()) {
      throw new ApiError("Máy chủ không trả về token đăng nhập hợp lệ", 502);
    }
    sessionStorage.setItem("driverguard_token", data.access_token);
  },
  logout() {
    sessionStorage.removeItem("driverguard_token");
  },
  hasToken: () => Boolean(token()),

  dashboard: () => request<DashboardSummary>("/dashboard/summary"),
  alertTrend: (days = 7) => request<AlertTrendPoint[]>(`/dashboard/alert-trend?days=${days}`),
  recentAlerts: (limit = 5) => request<RecentAlert[]>(`/dashboard/recent-alerts?limit=${limit}`),
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

  bindings: (params: Record<string, string> = {}) =>
    request<DeviceBinding[]>(`/device-bindings${query(params)}`),
  createBinding: (data: { userId: string; deviceId: string }) =>
    request<DeviceBinding>("/device-bindings", { method: "POST", body: JSON.stringify(data) }),
  unbindDevice: (id: string) => request<DeviceBinding>(`/device-bindings/${id}/unbind`, { method: "PATCH" }),

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
  detectionSettings: () => request<DetectionSetting[]>("/detection-settings"),
  saveDetectionSettings: (data: Omit<DetectionSetting, "id" | "updatedAt">) =>
    request<DetectionSetting>("/detection-settings", { method: "POST", body: JSON.stringify(data) }),
  deviceHealth: (deviceId?: string) =>
    request<DeviceHealth[]>(`/device-health${query({ device_id: deviceId })}`),
  auditLogs: (params: Record<string, string | number | undefined> = {}) =>
    request<AuditLog[]>(`/audit-logs${query({ page: 1, page_size: 100, ...params })}`),
  search: (term: string, limit = 20) => request<SearchResult[]>(`/search${query({ q: term, limit })}`),
  exportReportUrl: () => `${API_BASE_URL}/reports/export`,
  exportReport: () => request<Blob>("/reports/export", {}, "blob"),
};
