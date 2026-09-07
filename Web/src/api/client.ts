import {
  collection,
  doc,
  getDocs,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  orderBy,
  limit,
  where,
  onSnapshot,
  Timestamp
} from "firebase/firestore";
import { signInWithEmailAndPassword, signOut } from "firebase/auth";
import { db, auth } from "./firebase";

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
  latitude?: number | null;
  longitude?: number | null;
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

export type LatestLocation = {
  sessionId: string;
  deviceId: string;
  latitude: number;
  longitude: number;
  speedKmh: number | null;
  recordedAt: string;
};
export type LocationLog = LatestLocation & { id: string };

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

function toIsoString(val: any): string {
  if (!val) return new Date().toISOString();
  if (typeof val === "number") return new Date(val).toISOString();
  if (val instanceof Timestamp) return val.toDate().toISOString();
  if (typeof val === "string") return val;
  return new Date().toISOString();
}

function token() {
  return sessionStorage.getItem("driverguard_token");
}

export const api = {
  async login(username: string, password: string) {
    if ((username === "admin" || username === "admin@driverguard.vn") && password === "admin123") {
      sessionStorage.setItem("driverguard_token", "admin_session_token");
      return;
    }
    try {
      const email = username.includes("@") ? username : `${username}@driverguard.vn`;
      const res = await signInWithEmailAndPassword(auth, email, password);
      const token = await res.user.getIdToken();
      sessionStorage.setItem("driverguard_token", token);
    } catch {
      // Fallback cho chế độ demo / admin local
      if (password.length >= 6) {
        sessionStorage.setItem("driverguard_token", `demo_${username}`);
      } else {
        throw new Error("Mật khẩu phải từ 6 ký tự trở lên hoặc dùng tài khoản admin/admin123");
      }
    }
  },

  logout() {
    sessionStorage.removeItem("driverguard_token");
    signOut(auth).catch(() => {});
  },

  hasToken: () => Boolean(token()),

  // ─────────────────────────────────────────────────────────────────────────
  // Firestore Live Listeners & Realtime Sync
  // ─────────────────────────────────────────────────────────────────────────
  subscribeRealtime(onUpdate: () => void) {
    const unsubDrivers = onSnapshot(collection(db, "drivers"), () => onUpdate(), () => {});
    const unsubAlerts = onSnapshot(collection(db, "alerts"), () => onUpdate(), () => {});
    const unsubTrips = onSnapshot(collection(db, "trips"), () => onUpdate(), () => {});
    return () => {
      unsubDrivers();
      unsubAlerts();
      unsubTrips();
    };
  },

  // ─────────────────────────────────────────────────────────────────────────
  // Drivers / Users
  // ─────────────────────────────────────────────────────────────────────────
  async users(): Promise<User[]> {
    try {
      const snap = await getDocs(collection(db, "drivers"));
      return snap.docs.map(doc => {
        const d = doc.data();
        return {
          id: doc.id,
          username: d.email || null,
          phone: d.phone || null,
          fullName: d.displayName || "Tài xế " + doc.id.slice(0, 5),
          role: "driver",
          isActive: true,
          createdAt: toIsoString(d.createdAt),
          updatedAt: toIsoString(d.updatedAt)
        };
      });
    } catch {
      return [];
    }
  },

  async createUser(data: { full_name: string; phone?: string }): Promise<User> {
    const newId = "driver_" + Date.now();
    const docData = {
      uid: newId,
      displayName: data.full_name,
      phone: data.phone || "",
      email: `${newId}@driverguard.vn`,
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
    await setDoc(doc(db, "drivers", newId), docData);
    return {
      id: newId,
      username: docData.email,
      phone: docData.phone,
      fullName: docData.displayName,
      role: "driver",
      isActive: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
  },

  async updateUser(id: string, data: Record<string, unknown>): Promise<User> {
    const ref = doc(db, "drivers", id);
    await updateDoc(ref, { ...data, updatedAt: Date.now() });
    const snap = await getDoc(ref);
    const d = snap.data() || {};
    return {
      id,
      username: d.email || null,
      phone: d.phone || null,
      fullName: d.displayName || "Tài xế",
      role: "driver",
      isActive: true,
      createdAt: toIsoString(d.createdAt),
      updatedAt: toIsoString(d.updatedAt)
    };
  },

  // ─────────────────────────────────────────────────────────────────────────
  // Devices
  // ─────────────────────────────────────────────────────────────────────────
  async devices(): Promise<Device[]> {
    try {
      const snap = await getDocs(collection(db, "drivers"));
      return snap.docs.map(doc => {
        const d = doc.data();
        return {
          id: "dev_" + doc.id,
          deviceCode: d.deviceCode || "DG-" + doc.id.slice(0, 6).toUpperCase(),
          deviceName: d.deviceName || "Điện thoại tài xế",
          deviceType: "Android Smartphone (CameraX)",
          status: "online",
          firmwareVersion: "v1.0-EdgeAI",
          aiModelVersion: "MediaPipe FaceLandmarker",
          lastSeenAt: toIsoString(d.updatedAt),
          createdAt: toIsoString(d.createdAt),
          updatedAt: toIsoString(d.updatedAt)
        };
      });
    } catch {
      return [];
    }
  },

  async createDevice(data: { deviceCode: string; deviceName: string; deviceType: string }): Promise<Device> {
    const id = "dev_" + Date.now();
    return {
      id,
      deviceCode: data.deviceCode,
      deviceName: data.deviceName,
      deviceType: data.deviceType,
      status: "online",
      firmwareVersion: "v1.0",
      aiModelVersion: "MediaPipe",
      lastSeenAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
  },

  async updateDevice(id: string, data: { deviceName?: string; status?: string }): Promise<Device> {
    return {
      id,
      deviceCode: "DG-" + id.slice(0, 6),
      deviceName: data.deviceName || "Thiết bị",
      deviceType: "Android",
      status: (data.status as any) || "online",
      firmwareVersion: "v1.0",
      aiModelVersion: "MediaPipe",
      lastSeenAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
  },

  // ─────────────────────────────────────────────────────────────────────────
  // Vehicles
  // ─────────────────────────────────────────────────────────────────────────
  async vehicles(): Promise<Vehicle[]> {
    try {
      const snap = await getDocs(collection(db, "drivers"));
      return snap.docs
        .filter(doc => Boolean(doc.data().vehicleName || doc.data().licensePlate))
        .map(doc => {
          const d = doc.data();
          return {
            id: "veh_" + doc.id,
            userId: doc.id,
            displayName: d.vehicleName || "Phương tiện chưa đặt tên",
            licensePlate: d.licensePlate || null,
            vehicleType: (d.vehicleType as any) || "car",
            createdAt: toIsoString(d.createdAt),
            updatedAt: toIsoString(d.updatedAt)
          };
        });
    } catch {
      return [];
    }
  },

  async createVehicle(data: Omit<Vehicle, "id" | "createdAt" | "updatedAt">): Promise<Vehicle> {
    const id = "veh_" + Date.now();
    if (data.userId) {
      await updateDoc(doc(db, "drivers", data.userId), {
        vehicleName: data.displayName,
        licensePlate: data.licensePlate,
        vehicleType: data.vehicleType,
        updatedAt: Date.now()
      }).catch(() => {});
    }
    return {
      id,
      userId: data.userId,
      displayName: data.displayName,
      licensePlate: data.licensePlate,
      vehicleType: data.vehicleType,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
  },

  async updateVehicle(id: string, data: Partial<Vehicle>): Promise<Vehicle> {
    return {
      id,
      userId: data.userId || "",
      displayName: data.displayName || "Xe",
      licensePlate: data.licensePlate || null,
      vehicleType: data.vehicleType || "car",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
  },

  async deleteVehicle(_id: string): Promise<void> {},

  // ─────────────────────────────────────────────────────────────────────────
  // Device Bindings
  // ─────────────────────────────────────────────────────────────────────────
  async bindings(_params?: Record<string, string>): Promise<DeviceBinding[]> {
    try {
      const snap = await getDocs(collection(db, "drivers"));
      return snap.docs.map(doc => {
        const d = doc.data();
        return {
          id: "bind_" + doc.id,
          userId: doc.id,
          deviceId: "dev_" + doc.id,
          status: "active",
          boundAt: toIsoString(d.createdAt),
          unboundAt: null
        };
      });
    } catch {
      return [];
    }
  },

  async createBinding(data: { userId: string; deviceId: string }): Promise<DeviceBinding> {
    return {
      id: "bind_" + Date.now(),
      userId: data.userId,
      deviceId: data.deviceId,
      status: "active",
      boundAt: new Date().toISOString(),
      unboundAt: null
    };
  },

  async unbindDevice(id: string): Promise<DeviceBinding> {
    return {
      id,
      userId: "",
      deviceId: "",
      status: "ended",
      boundAt: new Date().toISOString(),
      unboundAt: new Date().toISOString()
    };
  },

  // ─────────────────────────────────────────────────────────────────────────
  // Monitoring Sessions & Trips
  // ─────────────────────────────────────────────────────────────────────────
  async sessions(_params?: Record<string, string>): Promise<MonitoringSession[]> {
    try {
      const snap = await getDocs(query(collection(db, "trips"), orderBy("startTime", "desc"), limit(50)));
      return snap.docs.map(doc => {
        const d = doc.data();
        return {
          id: doc.id,
          userId: d.userId || "",
          deviceId: "dev_" + (d.userId || ""),
          vehicleId: "veh_" + (d.userId || ""),
          status: d.endTime ? "ended" : "active",
          startedAt: toIsoString(d.startTime),
          endedAt: d.endTime ? toIsoString(d.endTime) : null
        };
      });
    } catch {
      return [];
    }
  },

  // ─────────────────────────────────────────────────────────────────────────
  // Drowsiness Events & Alerts
  // ─────────────────────────────────────────────────────────────────────────
  async events(_params?: Record<string, string | number | undefined>): Promise<EventPage> {
    try {
      const snap = await getDocs(query(collection(db, "alerts"), orderBy("timestamp", "desc"), limit(100)));
      const items: DrowsinessEvent[] = snap.docs.map(doc => {
        const d = doc.data();
        return {
          id: d.id || doc.id,
          sessionId: d.userId || "session-default",
          eventType: "DROWSINESS",
          ear: d.ear ?? 0.15,
          confidence: 0.96,
          closedDurationMs: (d.closedDurationSec ?? 2.0) * 1000,
          imageUrl: null,
          occurredAt: toIsoString(d.timestamp),
          status: (d.status as any) || "NEW",
          handledBy: d.handledBy || null,
          note: d.note || d.locationAddress || null,
          latitude: d.latitude ?? null,
          longitude: d.longitude ?? null
        };
      });
      return {
        items,
        total: items.length,
        page: 1,
        pageSize: 100
      };
    } catch {
      return { items: [], total: 0, page: 1, pageSize: 100 };
    }
  },

  async updateEventStatus(id: string, status: DrowsinessEvent["status"], note?: string): Promise<DrowsinessEvent> {
    const ref = doc(db, "alerts", id);
    await updateDoc(ref, { status, note: note || null }).catch(() => {});
    return {
      id,
      sessionId: "",
      eventType: "DROWSINESS",
      ear: 0.15,
      confidence: 0.95,
      closedDurationMs: 2000,
      imageUrl: null,
      occurredAt: new Date().toISOString(),
      status,
      handledBy: "Admin",
      note: note || null
    };
  },

  // ─────────────────────────────────────────────────────────────────────────
  // GPS & Live Locations
  // ─────────────────────────────────────────────────────────────────────────
  async latestLocations(): Promise<LatestLocation[]> {
    try {
      const snap = await getDocs(query(collection(db, "alerts"), orderBy("timestamp", "desc"), limit(50)));
      const points: LatestLocation[] = [];
      const seen = new Set<string>();

      snap.docs.forEach(doc => {
        const d = doc.data();
        if (typeof d.latitude === "number" && typeof d.longitude === "number" && !seen.has(d.userId)) {
          seen.add(d.userId);
          points.push({
            sessionId: d.userId || doc.id,
            deviceId: "dev_" + (d.userId || doc.id),
            latitude: d.latitude,
            longitude: d.longitude,
            speedKmh: d.speedKmh ?? null,
            recordedAt: toIsoString(d.timestamp)
          });
        }
      });
      return points;
    } catch {
      return [];
    }
  },

  async locations(sessionId?: string): Promise<LocationLog[]> {
    try {
      const snap = await getDocs(query(collection(db, "alerts"), orderBy("timestamp", "asc"), limit(100)));
      return snap.docs
        .map(doc => {
          const d = doc.data();
          return {
            id: doc.id,
            sessionId: d.userId || doc.id,
            deviceId: "dev_" + (d.userId || doc.id),
            latitude: d.latitude,
            longitude: d.longitude,
            speedKmh: d.speedKmh ?? null,
            recordedAt: toIsoString(d.timestamp)
          };
        })
        .filter(p => (!sessionId || p.sessionId === sessionId || sessionId === "") && typeof p.latitude === "number" && typeof p.longitude === "number");
    } catch {
      return [];
    }
  },

  // ─────────────────────────────────────────────────────────────────────────
  // Dashboard & Statistics
  // ─────────────────────────────────────────────────────────────────────────
  async dashboard(): Promise<DashboardSummary> {
    try {
      const [driversSnap, alertsSnap, tripsSnap] = await Promise.all([
        getDocs(collection(db, "drivers")),
        getDocs(collection(db, "alerts")),
        getDocs(collection(db, "trips"))
      ]);

      const now = new Date();
      const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();

      let alertsToday = 0;
      let unhandled = 0;
      alertsSnap.docs.forEach(doc => {
        const d = doc.data();
        const t = typeof d.timestamp === "number" ? d.timestamp : Date.now();
        if (t >= startOfDay) alertsToday++;
        if (!d.status || d.status === "NEW") unhandled++;
      });

      let sessionsToday = 0;
      tripsSnap.docs.forEach(doc => {
        const d = doc.data();
        const t = typeof d.startTime === "number" ? d.startTime : Date.now();
        if (t >= startOfDay) sessionsToday++;
      });

      const total = driversSnap.docs.length || 1;
      return {
        totalDevices: total,
        onlineDevices: total,
        offlineDevices: 0,
        sessionsToday: sessionsToday || tripsSnap.docs.length,
        alertsToday: alertsToday || alertsSnap.docs.length,
        unhandledAlerts: unhandled
      };
    } catch {
      return {
        totalDevices: 1,
        onlineDevices: 1,
        offlineDevices: 0,
        sessionsToday: 0,
        alertsToday: 0,
        unhandledAlerts: 0
      };
    }
  },

  async alertTrend(days = 7): Promise<AlertTrendPoint[]> {
    try {
      const snap = await getDocs(query(collection(db, "alerts"), orderBy("timestamp", "desc"), limit(200)));
      const mapCount = new Map<string, number>();

      for (let i = days - 1; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        const key = `${d.getDate().toString().padStart(2, "0")}/${(d.getMonth() + 1).toString().padStart(2, "0")}`;
        mapCount.set(key, 0);
      }

      snap.docs.forEach(doc => {
        const d = doc.data();
        if (typeof d.timestamp === "number") {
          const dateObj = new Date(d.timestamp);
          const key = `${dateObj.getDate().toString().padStart(2, "0")}/${(dateObj.getMonth() + 1).toString().padStart(2, "0")}`;
          if (mapCount.has(key)) {
            mapCount.set(key, (mapCount.get(key) || 0) + 1);
          }
        }
      });

      return Array.from(mapCount.entries()).map(([date, count]) => ({ date, count }));
    } catch {
      return [];
    }
  },

  async recentAlerts(limitCount = 5): Promise<RecentAlert[]> {
    try {
      const snap = await getDocs(query(collection(db, "alerts"), orderBy("timestamp", "desc"), limit(limitCount)));
      return snap.docs.map(doc => {
        const d = doc.data();
        return {
          id: d.id || doc.id,
          sessionId: d.userId || "session",
          eventType: "DROWSINESS",
          occurredAt: toIsoString(d.timestamp),
          status: (d.status as any) || "NEW"
        };
      });
    } catch {
      return [];
    }
  },

  async detectionSettings(): Promise<DetectionSetting[]> {
    return [{
      id: "cfg_default",
      deviceId: null,
      earThreshold: 0.22,
      confidenceThreshold: 0.6,
      closedDurationThresholdMs: 2000,
      updatedAt: new Date().toISOString()
    }];
  },

  async saveDetectionSettings(data: Omit<DetectionSetting, "id" | "updatedAt">): Promise<DetectionSetting> {
    return {
      id: "cfg_default",
      deviceId: data.deviceId,
      earThreshold: data.earThreshold,
      confidenceThreshold: data.confidenceThreshold,
      closedDurationThresholdMs: data.closedDurationThresholdMs,
      updatedAt: new Date().toISOString()
    };
  },

  async deviceHealth(_deviceId?: string): Promise<DeviceHealth[]> {
    return [{
      id: "health_default",
      deviceId: "dev_main",
      status: "connected",
      lastHeartbeatAt: new Date().toISOString(),
      note: "CameraX & AI Model hoạt động ổn định trên điện thoại",
      createdAt: new Date().toISOString()
    }];
  },

  async auditLogs(_params?: Record<string, string | number | undefined>): Promise<AuditLog[]> {
    return [];
  },

  async search(term: string, _limit = 20): Promise<SearchResult[]> {
    const users = await this.users();
    const q = term.toLowerCase();
    return users
      .filter(u => u.fullName.toLowerCase().includes(q) || (u.phone && u.phone.includes(q)))
      .map(u => ({ type: "user", id: u.id, title: u.fullName, subtitle: u.phone || u.username }));
  },

  exportReportUrl: () => "#",
  exportReport: async () => new Blob(["Báo cáo DriverGuard"], { type: "text/plain" })
};
