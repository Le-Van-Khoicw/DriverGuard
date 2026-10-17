import { getFunctions, httpsCallable, connectFunctionsEmulator } from 'firebase/functions';
import { collection, onSnapshot } from 'firebase/firestore';
import { app, db } from './firebase';

export type Staff = { uid: string; role: 'admin' | 'dispatcher'; displayName: string; employeeCode: string; active: boolean; phone?: string; email?: string };
export type Item = { name: string; quantity: number; unit: string };
export type Order = { id: string; customer: string; customerPhone: string; sender: string; senderPhone: string; pickupAddress: string; receiver: string; receiverPhone: string; deliveryAddress: string; items: Item[]; weightKg: number; start: number; end: number; status: string; tripId: string | null; note: string; createdBy: string };
export type Stop = { id: string; orderId: string; kind: 'pickup' | 'delivery'; address: string; contact: string; phone: string; items: Item[]; status: string; quantities?: number[]; note?: string; actualContact?: string; completedAt?: number; driverId?: string };
export type Trip = { id: string; name: string; driverId: string; driverName: string; vehicleId: string; vehiclePlate: string; dispatcherId: string; createdBy: string; start: number; end: number; actualStart?: number; actualEnd?: number; status: string; assignmentVersion: number; orderIds: string[]; stops: Stop[]; updatedAt: number; history: { from: string | null; to: string; by: string; at: number; reason: string; location: string; cargoNote: string }[]; resolution?: { note: string; by: string }; issueReason?: string; declineReason?: string };
export type FleetVehicle = { id: string; plate: string; capacityKg: number; status: string };
export type Driver = { id: string; displayName: string; phone: string; email?: string; employeeCode?: string; accountStatus: string; qualificationStatus?: string; approvedVehicleIds?: string[]; licenseClass?: string; licenseExpiresAt?: number; healthExpiresAt?: number };
export type Leave = { id: string; driverId: string; start: number; end: number; reason: string; status: string; affectedTripIds?: string[] };
export type Report = { id: string; userId: string; dispatchTripId?: string; assignmentVersion?: number; startTime: number; safetyScore: number | null; hasSufficientData: boolean; measurementCoverage: number; totalAlerts: number; aiDiagnosis: string };
export const labels: Record<string, string> = { ready: 'Sẵn sàng', assigned: 'Đã giao chuyến', accepted: 'Đã nhận', in_progress: 'Đang chạy', handover_pending: 'Chờ nhận bàn giao', declined: 'Từ chối', needs_reassignment: 'Cần giao lại', completed: 'Hoàn thành', completed_issues: 'Kết thúc có vấn đề', cancelled: 'Đã hủy', delivered: 'Đã giao đủ', delivery_issue: 'Giao có vấn đề', pending: 'Chờ xử lý', approved: 'Đã duyệt', rejected: 'Từ chối', active: 'Hoạt động', locked: 'Đã khóa', full: 'Đủ', partial: 'Thiếu', failed: 'Thất bại', available: 'Sẵn sàng', maintenance: 'Bảo trì', inactive: 'Ngừng sử dụng' };
const functions = getFunctions(app, 'asia-southeast1');
if (import.meta.env.VITE_FIREBASE_EMULATORS === 'true') connectFunctionsEmulator(functions, '127.0.0.1', 5001);
const callable = httpsCallable<Record<string, unknown>, { id: string }>(functions, 'dispatchCommand');
export async function command(action: string, data: Record<string, unknown>, operationId = crypto.randomUUID()) {
  return (await callable({ ...data, action, operationId })).data;
}
export function watch<T>(name: string, update: (rows: T[]) => void, error: (e: Error) => void) {
  return onSnapshot(collection(db, name), snapshot => update(snapshot.docs.map(d => ({ ...d.data(), id: d.id }) as T)), error);
}

export type Alert = { id: string; userId: string; dispatchTripId?: string; monitoringSessionId?: string; timestamp: number; ear?: number; closedDurationSec?: number; latitude?: number; longitude?: number; status?: string; note?: string };

export type CompanyAccount = { uid: string; email: string; displayName: string; emailVerified: boolean; disabled: boolean; providers: string[]; staffRole: string | null; staffActive: boolean; driverStatus: string | null; createdAt: string; lastSignInAt: string | null };
export async function listCompanyAccounts(pageToken?: string) {
  return (await httpsCallable<{ pageToken?: string }, { accounts: CompanyAccount[]; nextPageToken: string | null }>(functions, 'listCompanyAccounts')({ ...(pageToken ? { pageToken } : {}) })).data;
}
export type DriverDossier = {
  driverId: string; revision: number; status: string; fullName: string; phone: string; birthDate: string; address: string;
  identityNumber: string; licenseNumber: string; licenseClass: string; licenseIssuedDate: string; licenseExpiresDate: string;
  healthCheckedDate: string; healthExpiresDate: string; emergencyName: string; emergencyPhone: string;
  reviewNote?: string; reviewedBy?: string; reviewedAt?: number; submittedAt: number;
  documents: { kind: string; path: string; name: string; contentType: string; size: number }[];
};
export function qualificationLabel(d: Driver) {
  if (d.qualificationStatus === 'approved' && ((d.licenseExpiresAt || 0) <= Date.now() || (d.healthExpiresAt || 0) <= Date.now())) return 'Hồ sơ hết hạn';
  return ({ approved: 'Đã duyệt hồ sơ', submitted: 'Hồ sơ chờ duyệt', rejected: 'Cần bổ sung hồ sơ' } as Record<string, string>)[d.qualificationStatus || ''] || 'Chưa nộp hồ sơ';
}
