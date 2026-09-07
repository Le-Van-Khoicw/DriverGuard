import { useEffect, useMemo, useRef, useState } from "react";
import * as L from "leaflet";
import "leaflet/dist/leaflet.css";
import "../styles/index.css";
import { api, Device, DrowsinessEvent, LatestLocation, MonitoringSession, User, Vehicle } from "../api/client";

export function hasCoordinates(point: { latitude?: number | null; longitude?: number | null }): point is { latitude: number; longitude: number } {
  return typeof point.latitude === "number" && Number.isFinite(point.latitude) && Math.abs(point.latitude) <= 90 && typeof point.longitude === "number" && Number.isFinite(point.longitude) && Math.abs(point.longitude) <= 180;
}
export const eventLabel = (type: string) => type === "ACCIDENT" ? "Tai nạn" : type === "DROWSINESS" ? "Buồn ngủ" : type;
const date = (value: string) => new Date(value).toLocaleString("vi-VN");
type MapPoint = { id: string; latitude: number; longitude: number; label: string; speed?: number | null; alert?: boolean };

function LocationMap({
  points,
  route,
  centerKey = 0,
  focusPoint
}: {
  points: MapPoint[];
  route: boolean;
  centerKey?: number;
  focusPoint?: { lat: number; lng: number; id: string; ts: number } | null;
}) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const layerGroup = useRef<L.LayerGroup | null>(null);
  const markersRef = useRef<Map<string, L.CircleMarker>>(new Map());
  const hasFittedInitial = useRef(false);
  const [tileError, setTileError] = useState(false);
  const [retry, setRetry] = useState(0);

  // ── Khởi tạo bản đồ Leaflet 1 lần duy nhất ──────────────────────────────
  useEffect(() => {
    if (!container.current) return;
    const instance = L.map(container.current, {
      minZoom: 1,
      maxZoom: 20,
      scrollWheelZoom: true,
      dragging: true,
      touchZoom: true,
      doubleClickZoom: true
    }).setView([16.05, 108.2], 6);

    map.current = instance;
    layerGroup.current = L.layerGroup().addTo(instance);

    const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(() => instance.invalidateSize()) : null;
    observer?.observe(container.current);

    return () => {
      observer?.disconnect();
      instance.remove();
      map.current = null;
      layerGroup.current = null;
      markersRef.current.clear();
    };
  }, []);

  // ── Nạp nền Google Maps tiếng Việt ───────────────────────────────────────
  useEffect(() => {
    const instance = map.current;
    if (!instance) return;
    let disposed = false;
    let layer: L.Layer | undefined;
    setTileError(false);

    void import("./mapBackground").then(({ addMapBackground }) => {
      if (!disposed) layer = addMapBackground(instance, failed => { if (!disposed) setTileError(failed); });
    }).catch(() => { if (!disposed) setTileError(true); });

    return () => { disposed = true; layer?.remove(); };
  }, [retry]);

  // ── Vẽ markers & polyline — KHÔNG gọi fitBounds ở đây ───────────────────
  // Dùng dependency [points, route] — CHỈ chạy khi dữ liệu GPS thực sự đổi
  // (nhờ useMemo ở LocationPanel, points ổn định khi mày chỉ setFocusPoint)
  useEffect(() => {
    const instance = map.current;
    const layer = layerGroup.current;
    if (!instance || !layer) return;

    layer.clearLayers();
    markersRef.current.clear();
    const valid = points.filter(hasCoordinates);
    const track = valid.filter(p => !p.alert);

    if (route && track.length > 1) {
      L.polyline(track.map(p => [p.latitude, p.longitude] as L.LatLngTuple), { color: "#0284c7", weight: 4 }).addTo(layer);
    }

    valid.forEach((p, i) => {
      const popupDiv = document.createElement("div");
      popupDiv.style.minWidth = "180px";
      popupDiv.style.fontSize = "13px";
      popupDiv.style.lineHeight = "1.5";
      popupDiv.innerHTML = `
        <div style="font-weight: 700; color: ${p.alert ? '#dc2626' : '#0369a1'}; margin-bottom: 4px;">
          ${p.alert ? '⚠️ CẢNH BÁO NGUY HIỂM' : '📍 Vị trí xe'}
        </div>
        <div style="color: #334155; margin-bottom: 2px;">${p.label}</div>
        <div style="color: #64748b; font-size: 11px;">Tọa độ: ${p.latitude.toFixed(6)}, ${p.longitude.toFixed(6)}</div>
      `;

      const marker = L.circleMarker([p.latitude, p.longitude], {
        radius: p.alert ? 11 : route ? 7 : 10,
        color: p.alert ? "#dc2626" : i === 0 && route ? "#16a34a" : "#0284c7",
        fillColor: p.alert ? "#ef4444" : i === 0 && route ? "#22c55e" : "#38bdf8",
        fillOpacity: 0.9,
        weight: 2
      });

      marker.bindPopup(popupDiv);
      // Khi click marker → bay tới vị trí đó (KHÔNG dùng React state để tránh re-render)
      marker.on("click", () => {
        instance.flyTo([p.latitude, p.longitude], 17, { duration: 1.0 });
        marker.openPopup();
      });

      marker.addTo(layer);
      markersRef.current.set(p.id, marker);
    });

    // Lần đầu tiên có dữ liệu → tự fitBounds một lần
    if (valid.length > 0 && !hasFittedInitial.current) {
      instance.fitBounds(L.latLngBounds(valid.map(p => [p.latitude, p.longitude])), { padding: [40, 40], maxZoom: 16 });
      hasFittedInitial.current = true;
    }
  }, [points, route]);
  // NOTE: centerKey KHÔNG có trong dependency này — có effect riêng bên dưới

  // ── Nút "Căn giữa GPS": chỉ fitBounds khi user bấm nút ──────────────────
  useEffect(() => {
    if (centerKey === 0) return; // Bỏ qua lần mount đầu tiên
    const instance = map.current;
    if (!instance) return;
    const valid = Array.from(markersRef.current.values()).map(m => m.getLatLng());
    if (valid.length > 0) {
      instance.fitBounds(L.latLngBounds(valid), { padding: [40, 40], maxZoom: 16 });
    }
  }, [centerKey]);

  // ── Bay tới điểm được click từ bảng dữ liệu bên dưới ────────────────────
  useEffect(() => {
    const instance = map.current;
    if (!instance || !focusPoint) return;
    instance.flyTo([focusPoint.lat, focusPoint.lng], 17, { duration: 1.0 });
    // Mở popup của marker tương ứng (nếu tìm thấy)
    const targetMarker = markersRef.current.get(focusPoint.id);
    if (targetMarker) {
      setTimeout(() => targetMarker.openPopup(), 800); // Đợi flyTo gần xong rồi mở popup
    }
  }, [focusPoint]);

  return (
    <>
      <div ref={container} className="location-map" aria-label={route ? "Bản đồ lịch trình" : "Bản đồ vị trí xe"} />
      {tileError && (
        <div role="status">
          <p>Không tải được nền bản đồ. Kiểm tra kết nối mạng rồi thử lại.</p>
          <button className="secondary-button" onClick={() => setRetry(value => value + 1)}>Thử tải lại</button>
        </div>
      )}
    </>
  );
}

export function LocationPanel({
  sessionId,
  devices = [],
  sessions = [],
  vehicles = [],
  users = [],
  event,
  openSession
}: {
  sessionId?: string;
  devices?: Device[];
  sessions?: MonitoringSession[];
  vehicles?: Vehicle[];
  users?: User[];
  event?: DrowsinessEvent;
  openSession?: (id: string) => void;
}) {
  const [points, setPoints] = useState<LatestLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [updated, setUpdated] = useState("");
  const [refresh, setRefresh] = useState(0);
  const [centerTrigger, setCenterTrigger] = useState(0);
  const [focusPoint, setFocusPoint] = useState<{ lat: number; lng: number; id: string; ts: number } | null>(null);

  useEffect(() => {
    let disposed = false;
    let timer: ReturnType<typeof setTimeout>;
    setLoading(true); setError(""); setUpdated("");

    async function load() {
      try {
        const result = sessionId ? await api.locations(sessionId) : await api.latestLocations();
        if (disposed) return;
        const sorted = [...result].sort((a, b) => Date.parse(a.recordedAt) - Date.parse(b.recordedAt));
        setPoints(sessionId ? sorted : [...new Map(sorted.map(p => [p.deviceId, p])).values()]);
        setUpdated(new Date().toLocaleTimeString("vi-VN")); setError("");
      } catch (reason) {
        if (!disposed) setError(reason instanceof Error ? reason.message : "Không thể tải vị trí");
      } finally {
        if (!disposed) {
          setLoading(false);
          timer = setTimeout(load, 10000);
        }
      }
    }
    void load();
    return () => { disposed = true; clearTimeout(timer); };
  }, [sessionId, refresh]);

  const label = (p: LatestLocation) => {
    const session = sessions.find(s => s.id === p.sessionId);
    const user = users.find(u => u.id === session?.userId || u.id === p.sessionId || u.id === p.deviceId?.replace("dev_", ""));
    const vehicle = vehicles.find(v => v.id === session?.vehicleId);
    const dev = devices.find(d => d.id === p.deviceId);

    const parts = [
      user?.fullName || user?.username,
      vehicle?.licensePlate || vehicle?.displayName,
      dev?.deviceCode || p.deviceId
    ].filter(Boolean);

    return parts.length > 0 ? parts.join(" · ") : p.deviceId;
  };

  // useMemo: mapPoints chỉ tính lại khi GPS data hoặc metadata thực sự thay đổi
  // Quan trọng: ngăn LocationMap re-draw khi chỉ setFocusPoint (click vào bảng)
  const mapPoints = useMemo<MapPoint[]>(() => {
    const result: MapPoint[] = points.filter(hasCoordinates).map((p, i) => ({
      id: `${p.deviceId}-${p.recordedAt}-${i}`,
      latitude: p.latitude,
      longitude: p.longitude,
      speed: p.speedKmh,
      label: `${label(p)} · ${date(p.recordedAt)} · ${p.speedKmh != null ? `${Math.round(p.speedKmh)} km/h` : "—"}`
    }));
    if (event && hasCoordinates(event)) {
      result.push({
        id: `alert-${event.id}`,
        latitude: event.latitude,
        longitude: event.longitude,
        label: `${eventLabel(event.eventType)} · ${date(event.occurredAt)}`,
        alert: true
      });
    }
    return result;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [points, sessions, vehicles, users, devices, event]);

  const handleZoomTo = (latitude: number, longitude: number, id: string) => {
    setFocusPoint({ lat: latitude, lng: longitude, id, ts: Date.now() });
  };

  return (
    <section className="panel location-panel">
      <div className="panel-heading">
        <div>
          <h2>{sessionId ? "Lịch trình di chuyển" : "Vị trí xe đang hoạt động"}</h2>
          <p>Tự cập nhật GPS ngầm {updated && ` · Cập nhật lúc ${updated}`}</p>
        </div>
        <div style={{ display: "flex", gap: "8px" }}>
          <button className="secondary-button" onClick={() => setCenterTrigger(v => v + 1)}>📍 Căn giữa GPS</button>
          <button className="secondary-button" onClick={() => setRefresh(v => v + 1)}>Làm mới dữ liệu</button>
        </div>
      </div>
      {sessionId && <p className="location-session">Phiên: {sessionId}</p>}
      {error && <div className="inline-error" role="alert">{error}{updated && " · Đang hiển thị dữ liệu lần tải thành công trước."}</div>}
      {loading && <p role="status">Đang tải vị trí...</p>}
      <LocationMap points={mapPoints} route={Boolean(sessionId)} centerKey={centerTrigger} focusPoint={focusPoint} />
      {!loading && !error && !points.length && <p className="empty-state">{sessionId ? "Phiên này chưa có điểm GPS." : "Chưa có vị trí GPS của xe đang hoạt động."}</p>}
      {sessionId && <p>Đường xanh nối các điểm GPS theo thời gian; điểm đầu màu xanh lá, cảnh báo màu đỏ. Nhấp vào điểm hoặc bảng để phóng to.</p>}
      {points.length > 0 && (
        <div className="location-table">
          <table>
            <thead>
              <tr>
                <th>{sessionId ? "Điểm" : "Xe / Tài xế / Thiết bị"}</th>
                <th>Thời điểm GPS</th>
                <th>Vĩ độ, kinh độ</th>
                <th>Tốc độ</th>
                <th>Hành động</th>
              </tr>
            </thead>
            <tbody>
              {points.map((p, i) => {
                const pointId = `${p.deviceId}-${p.recordedAt}-${i}`;
                const valid = hasCoordinates(p);
                return (
                  <tr
                    key={pointId}
                    style={{ cursor: valid ? "pointer" : "default" }}
                    onClick={() => valid && handleZoomTo(p.latitude, p.longitude, pointId)}
                    title={valid ? "Nhấp để phóng to vị trí trên bản đồ" : undefined}
                  >
                    <td>{sessionId ? i + 1 : label(p)}</td>
                    <td>{date(p.recordedAt)}{Date.now() - Date.parse(p.recordedAt) > 60000 && !sessionId && <span className="badge badge-amber">GPS quá 1 phút</span>}</td>
                    <td>{valid ? `${p.latitude.toFixed(6)}, ${p.longitude.toFixed(6)}` : "Tọa độ không hợp lệ"}</td>
                    <td>{p.speedKmh == null ? "—" : `${Math.round(p.speedKmh)} km/h`}</td>
                    <td>
                      <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                        {valid && (
                          <button
                            type="button"
                            className="secondary-button"
                            style={{ padding: "4px 8px", fontSize: "12px" }}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleZoomTo(p.latitude, p.longitude, pointId);
                            }}
                          >
                            🎯 Zoom tới
                          </button>
                        )}
                        {!sessionId && openSession && (
                          <button
                            type="button"
                            className="text-button"
                            onClick={(e) => {
                              e.stopPropagation();
                              openSession(p.sessionId);
                            }}
                          >
                            Xem lịch trình
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
