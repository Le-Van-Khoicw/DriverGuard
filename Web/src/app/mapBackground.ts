import * as L from "leaflet";

export function addMapBackground(map: L.Map, _onError: (failed: boolean) => void): L.Layer {
  // Bản đồ Google Maps chính gốc với ngôn ngữ Tiếng Việt (hl=vi) và lãnh thổ Việt Nam (gl=VN)
  const layer = L.tileLayer("https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}&hl=vi&gl=VN", {
    subdomains: "0123",
    maxZoom: 20,
    attribution: '&copy; Google Maps'
  });

  layer.addTo(map);
  return layer;
}
