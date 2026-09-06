vi.mock("./mapBackground", () => ({ addMapBackground: () => ({ remove: vi.fn() }) }));
import { act, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { api, LatestLocation } from "../api/client";
import { hasCoordinates, LocationPanel } from "./LocationPanel";

vi.mock("../api/client", () => ({ api: { locations: vi.fn(), latestLocations: vi.fn() } }));
vi.mock("leaflet", () => {
  const layer = () => ({ addTo: vi.fn().mockReturnThis(), remove: vi.fn(), bindPopup: vi.fn().mockReturnThis(), on: vi.fn().mockReturnThis() });
  return { map: () => ({ setView: vi.fn().mockReturnThis(), remove: vi.fn(), fitBounds: vi.fn(), invalidateSize: vi.fn() }), tileLayer: layer, layerGroup: layer, circleMarker: layer, polyline: layer, latLngBounds: vi.fn() };
});
const point: LatestLocation = { deviceId: "device-1", sessionId: "session-1", latitude: 10.75, longitude: 106.7, speedKmh: 0, recordedAt: "2026-09-06T03:00:00Z" };
afterEach(() => { vi.useRealTimers(); vi.clearAllMocks(); });

it("accepts zero coordinates and rejects missing or invalid coordinates", () => {
  expect(hasCoordinates({ latitude: 0, longitude: 0 })).toBe(true);
  for (const latitude of [null, undefined, NaN, 91]) expect(hasCoordinates({ latitude, longitude: 0 })).toBe(false);
});
it("polls, preserves GPS on errors, recovers, and stops polling after unmount", async () => {
  vi.useFakeTimers();
  vi.mocked(api.latestLocations).mockResolvedValueOnce([point]).mockRejectedValueOnce(new Error("Mất kết nối GPS")).mockResolvedValue([]);
  const view = render(<LocationPanel />);
  await act(async () => {});
  expect(screen.getByText("0 km/h")).toBeVisible();
  await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
  expect(screen.getByRole("alert")).toHaveTextContent("Mất kết nối GPS");
  expect(screen.getByText("0 km/h")).toBeVisible();
  await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
  expect(screen.queryByRole("alert")).toBeNull();
  expect(screen.getByText("Chưa có vị trí GPS của xe đang hoạt động.")).toBeVisible();
  view.unmount();
  await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
  expect(api.latestLocations).toHaveBeenCalledTimes(3);
});
it("ignores late results from the previous session", async () => {
  let resolveOld!: (points: (LatestLocation & { id: string })[]) => void;
  vi.mocked(api.locations).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; })).mockResolvedValueOnce([]);
  const view = render(<LocationPanel sessionId="old" />);
  view.rerender(<LocationPanel sessionId="new" />);
  await screen.findByText("Phiên này chưa có điểm GPS.");
  await act(async () => { resolveOld([{ ...point, id: "old-point" }]); });
  expect(screen.queryByText("0 km/h")).toBeNull();
  expect(api.locations).toHaveBeenLastCalledWith("new");
});
