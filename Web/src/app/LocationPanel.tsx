import { useEffect, useRef, useState } from "react";
import * as L from "leaflet";
import "leaflet/dist/leaflet.css";
import "../styles/index.css";
import { api, Device, DrowsinessEvent, LatestLocation, MonitoringSession, Vehicle } from "../api/client";

export function hasCoordinates(point: { latitude?: number | null; longitude?: number | null }): point is { latitude: number; longitude: number } {
  return typeof point.latitude === "number" && Number.isFinite(point.latitude) && Math.abs(point.latitude) <= 90 && typeof point.longitude === "number" && Number.isFinite(point.longitude) && Math.abs(point.longitude) <= 180;
}
export const eventLabel = (type: string) => type === "ACCIDENT" ? "Tai nạn" : type === "DROWSINESS" ? "Buồn ngủ" : type;
const date = (value: string) => new Date(value).toLocaleString("vi-VN");
type MapPoint = { latitude: number; longitude: number; label: string; alert?: boolean };

function LocationMap({ points, route }: { points: MapPoint[]; route: boolean }) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const [tileError, setTileError] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!container.current) return;
    const instance = L.map(container.current, { minZoom: 1, maxZoom: 19 }).setView([16.05, 108.2], 5);
    map.current = instance;
    const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(() => instance.invalidateSize()) : null;
    observer?.observe(container.current);
    return () => { observer?.disconnect(); instance.remove(); map.current = null; };
  }, []);
  useEffect(() => {
    const instance = map.current;
    if (!instance) return;
    let disposed = false;
    let layer: L.Layer | undefined;
    setTileError(false);
    // Load the vector renderer separately so it does not inflate the initial app bundle.
    void import("./mapBackground").then(({ addMapBackground }) => {
      if (!disposed) layer = addMapBackground(instance, failed => { if (!disposed) setTileError(failed); });
    }).catch(() => { if (!disposed) setTileError(true); });
    return () => { disposed = true; layer?.remove(); };
  }, [retry]);
  useEffect(() => {
    const instance = map.current;
    if (!instance) return;
    const layer = L.layerGroup().addTo(instance);
    const valid = points.filter(hasCoordinates);
    const track = valid.filter(p => !p.alert);
    if (route && track.length > 1) L.polyline(track.map(p => [p.latitude, p.longitude] as L.LatLngTuple), { color: "#0284c7", weight: 4 }).addTo(layer);
    valid.forEach((p, i) => {
      const text = document.createElement("span"); text.textContent = p.label;
      L.circleMarker([p.latitude, p.longitude], { radius: p.alert ? 10 : route ? 5 : 9, color: p.alert ? "#dc2626" : i === 0 && route ? "#16a34a" : "#0284c7", fillOpacity: .85 }).bindPopup(text).addTo(layer);
    });
    if (valid.length) instance.fitBounds(L.latLngBounds(valid.map(p => [p.latitude, p.longitude])), { padding: [30, 30], maxZoom: 16 });
    return () => { layer.remove(); };
  }, [points, route]);
  return <><div ref={container} className="location-map" aria-label={route ? "Bản đồ lịch trình" : "Bản đồ vị trí xe"} />{tileError && <div role="status"><p>Không tải được đầy đủ nền bản đồ. Kiểm tra kết nối mạng rồi thử lại. Bạn vẫn có thể xem tọa độ bên dưới.</p><button className="secondary-button" onClick={() => setRetry(value => value + 1)}>Thử tải lại bản đồ</button></div>}</>;
}

export function LocationPanel({ sessionId, devices = [], sessions = [], vehicles = [], event, openSession }: {
  sessionId?: string; devices?: Device[]; sessions?: MonitoringSession[]; vehicles?: Vehicle[]; event?: DrowsinessEvent; openSession?: (id: string) => void;
}) {
  const [points, setPoints] = useState<LatestLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [updated, setUpdated] = useState("");
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let disposed = false;
    let timer: ReturnType<typeof setTimeout>;
    setPoints([]); setLoading(true); setError(""); setUpdated("");
    async function load() {
      try {
        const result = sessionId ? await api.locations(sessionId) : await api.latestLocations();
        if (disposed) return;
        const sorted = [...result].sort((a, b) => Date.parse(a.recordedAt) - Date.parse(b.recordedAt));
        setPoints(sessionId ? sorted : [...new Map(sorted.map(p => [p.deviceId, p])).values()]);
        setUpdated(new Date().toLocaleTimeString("vi-VN")); setError("");
      } catch (reason) { if (!disposed) setError(reason instanceof Error ? reason.message : "Không thể tải vị trí"); }
      finally { if (!disposed) { setLoading(false); timer = setTimeout(load, 10000); } }
    }
    void load();
    return () => { disposed = true; clearTimeout(timer); };
  }, [sessionId, refresh]);
  const label = (p: LatestLocation) => {
    const session = sessions.find(s => s.id === p.sessionId);
    const vehicle = vehicles.find(v => v.id === session?.vehicleId);
    return vehicle?.licensePlate || vehicle?.displayName || devices.find(d => d.id === p.deviceId)?.deviceCode || p.deviceId;
  };
  const mapPoints: MapPoint[] = points.filter(hasCoordinates).map(p => ({ ...p, label: `${label(p)} · ${date(p.recordedAt)} · ${p.speedKmh ?? "—"} km/h` }));
  if (event && hasCoordinates(event)) mapPoints.push({ latitude: event.latitude, longitude: event.longitude, label: `${eventLabel(event.eventType)} · ${date(event.occurredAt)}`, alert: true });
  return <section className="panel location-panel"><div className="panel-heading"><div><h2>{sessionId ? "Lịch trình di chuyển" : "Vị trí xe đang hoạt động"}</h2><p>Tự cập nhật mỗi 10 giây{updated && ` · Cập nhật lúc ${updated}`}</p></div><button className="secondary-button" onClick={() => setRefresh(v => v + 1)}>Làm mới GPS</button></div>
    {sessionId && <p className="location-session">Phiên: {sessionId}</p>}
    {error && <div className="inline-error" role="alert">{error}{updated && " · Đang hiển thị dữ liệu lần tải thành công trước."}</div>}
    {loading && <p role="status">Đang tải vị trí...</p>}
    <LocationMap points={mapPoints} route={Boolean(sessionId)} />
    {!loading && !error && !points.length && <p className="empty-state">{sessionId ? "Phiên này chưa có điểm GPS." : "Chưa có vị trí GPS của xe đang hoạt động."}</p>}
    {sessionId && <p>Đường xanh nối các điểm GPS theo thời gian; điểm đầu màu xanh lá, cảnh báo màu đỏ.</p>}
    {points.length > 0 && <div className="location-table"><table><thead><tr><th>{sessionId ? "Điểm" : "Xe / thiết bị"}</th><th>Thời điểm GPS</th><th>Vĩ độ, kinh độ</th><th>Tốc độ</th><th /></tr></thead><tbody>{points.map((p, i) => <tr key={`${p.deviceId}-${p.recordedAt}-${i}`}><td>{sessionId ? i + 1 : label(p)}</td><td>{date(p.recordedAt)}{Date.now() - Date.parse(p.recordedAt) > 60000 && !sessionId && <span className="badge badge-amber">GPS quá 1 phút</span>}</td><td>{hasCoordinates(p) ? `${p.latitude.toFixed(6)}, ${p.longitude.toFixed(6)}` : "Tọa độ không hợp lệ"}</td><td>{p.speedKmh == null ? "—" : `${p.speedKmh} km/h`}</td><td>{!sessionId && openSession && <button className="text-button" onClick={() => openSession(p.sessionId)}>Xem lịch trình</button>}</td></tr>)}</tbody></table></div>}
  </section>;
}
