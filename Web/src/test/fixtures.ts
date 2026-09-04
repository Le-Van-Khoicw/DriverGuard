export function fixtures() {
  const now = '2026-09-03T08:00:00Z';
  return {
    summary: { totalDevices: 1, onlineDevices: 1, offlineDevices: 0, sessionsToday: 1, alertsToday: 1, unhandledAlerts: 1 },
    trend: [{ date: '2026-09-03', count: 1 }],
    users: [{ id: 'user-1', username: null, phone: '0901234567', fullName: 'Nguyễn An', role: 'driver' as const, isActive: true, createdAt: now, updatedAt: now }],
    devices: [{ id: 'device-1', deviceCode: 'CAM-001', deviceName: 'Camera An', deviceType: 'edge-camera', status: 'online' as const, firmwareVersion: null, aiModelVersion: null, lastSeenAt: now, createdAt: now, updatedAt: now }],
    vehicles: [{ id: 'vehicle-1', userId: 'user-1', displayName: 'Xe An', licensePlate: '51A-12345', vehicleType: 'car' as const, createdAt: now, updatedAt: now }],
    sessions: [{ id: 'session-1', userId: 'user-1', deviceId: 'device-1', vehicleId: 'vehicle-1', status: 'active' as const, startedAt: now, endedAt: null }],
    events: [{ id: 'event-1', sessionId: 'session-1', eventType: 'DROWSINESS', ear: 0.15, confidence: 0.95, closedDurationMs: 2000, imageUrl: null, occurredAt: now, status: 'NEW' as const, handledBy: null, note: null }],
  };
}
