import * as L from "leaflet";
import { maplibreGL } from "@maplibre/maplibre-gl-leaflet";
import "maplibre-gl/dist/maplibre-gl.css";
import { setWorkerUrl } from "maplibre-gl";
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url";

setWorkerUrl(workerUrl);

export function addMapBackground(map: L.Map, onError: (failed: boolean) => void): L.Layer {
  const layer = maplibreGL({
    style: "https://tiles.openfreemap.org/styles/liberty",
    attributionControl: { customAttribution: '<a href="https://openfreemap.org/">OpenFreeMap</a> &copy; <a href="https://openmaptiles.org/">OpenMapTiles</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' },
  });
  try {
    layer.addTo(map);
    const renderer = layer.getMaplibreMap();
    map.getContainer().setAttribute("aria-busy", "true");
    renderer.on("error", () => { map.getContainer().setAttribute("aria-busy", "false"); onError(true); });
    renderer.on("idle", () => { if (renderer.areTilesLoaded()) { map.getContainer().setAttribute("aria-busy", "false"); onError(false); } });
    return layer;
  } catch {
    // Browsers without WebGL can still use the raster map if its host is reachable.
    if (map.hasLayer(layer)) map.removeLayer(layer);
    return L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>', maxZoom: 19,
    }).on("tileerror", () => onError(true)).addTo(map);
  }
}
