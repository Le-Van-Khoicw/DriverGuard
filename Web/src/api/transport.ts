import { addDoc, collection, doc, onSnapshot, runTransaction, setDoc, updateDoc } from "firebase/firestore";
import { auth, db } from "./firebase";
import type { User } from "./client";

export type DeliveryStop = { address: string; status: "pending" | "delivered" | "failed"; note: string };
export type Delivery = { id: string; title: string; userId: string; scheduledAt: number; status: "assigned" | "accepted" | "in_progress" | "completed" | "cancelled"; stops: DeliveryStop[]; createdAt: number; completedAt?: number };
export type Reminder = { id: string; userId: string; deliveryId: string; text: string; createdAt: number; readAt: number | null };
export type SafetyAlert = { id: string; userId: string; deliveryId?: string; timestamp: number; imageUrl?: string; latitude?: number; longitude?: number; excluded?: boolean };
export type Rules = { allowedAlerts: number; completionPoints: number; cleanBonus: number; violationPenalty: number };
export type Score = { id: string; userId: string; deliveryId: string; points: number; violations: number; completedAt: number; rules: Rules };
export const defaultRules: Rules = { allowedAlerts: 3, completionPoints: 10, cleanBonus: 5, violationPenalty: 2 };
export const millis = (value: unknown): number => typeof value === "number" ? value : value && typeof (value as { toMillis?: unknown }).toMillis === "function" ? (value as { toMillis: () => number }).toMillis() : 0;
export function validateRules(rules: Rules) {
  if (Object.values(rules).some(value => !Number.isSafeInteger(value) || value < 0 || value > 100000)) throw new Error("Các mức cấu hình phải là số nguyên từ 0 đến 100000.");
}
export function pointsFor(violations: number, rules: Rules) {
  validateRules(rules);
  if (!Number.isSafeInteger(violations) || violations < 0) throw new Error("Số vi phạm phải là số nguyên không âm.");
  return Math.max(0, rules.completionPoints + (violations === 0 ? rules.cleanBonus : -violations * rules.violationPenalty));
}
export function periodStart(period: "week" | "month", now = new Date()) {
  // Business calendar is always Asia/Ho_Chi_Minh (UTC+7).
  const local = new Date(now.getTime() + 7 * 3600000);
  const date = new Date(Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate()));
  if (period === "month") date.setUTCDate(1);
  else date.setUTCDate(date.getUTCDate() - (date.getUTCDay() + 6) % 7);
  return date.getTime() - 7 * 3600000;
}
export function ranking(scores: Score[], period: "week" | "month", now = new Date()) {
  const totals = new Map<string, { userId: string; points: number; trips: number }>();
  for (const score of scores.filter(s => s.completedAt >= periodStart(period, now) && s.completedAt <= now.getTime())) {
    const entry = totals.get(score.userId) ?? { userId: score.userId, points: 0, trips: 0 };
    entry.points += score.points; entry.trips++; totals.set(score.userId, entry);
  }
  return [...totals.values()].sort((a, b) => b.points - a.points || b.trips - a.trips || a.userId.localeCompare(b.userId));
}
export function listen<T>(name: string, next: (items: T[]) => void, error: (error: Error) => void) {
  return onSnapshot(collection(db, name), snap => next(snap.docs.map(item => ({ ...item.data(), id: item.id }) as T)), error);
}
export function listenRules(next: (rules: Rules) => void, error: (error: Error) => void) {
  return onSnapshot(doc(db, "transportSettings", "scoring"), snap => {
    const rules = { ...defaultRules, ...snap.data() }; try { validateRules(rules); next(rules); } catch (reason) { error(reason as Error); }
  }, error);
}
export async function createDelivery(data: Pick<Delivery, "title" | "userId" | "scheduledAt" | "stops">, users: User[]) {
  if (!data.title.trim() || !Number.isFinite(data.scheduledAt) || !data.stops.length || data.stops.some(stop => !stop.address.trim())) throw new Error("Nhập tên chuyến, ngày giờ và ít nhất một điểm giao.");
  const employee = users.find(user => user.id === data.userId && user.role === "driver");
  if (!employee || (employee.accountStatus ?? (employee.isActive ? "active" : "locked")) !== "active") throw new Error("Chỉ phân công cho nhân viên đang hoạt động.");
  return addDoc(collection(db, "deliveries"), { ...data, title: data.title.trim(), status: "assigned", createdAt: Date.now(), createdBy: auth.currentUser?.uid ?? null });
}
export async function sendReminder(userId: string, deliveryId: string, text: string) {
  if (!userId || !text.trim() || text.trim().length > 2000) throw new Error("Chọn nhân viên và nhập nội dung tối đa 2000 ký tự.");
  return addDoc(collection(db, "employeeMessages"), { userId, deliveryId, text: text.trim(), createdAt: Date.now(), readAt: null, senderId: auth.currentUser?.uid ?? null });
}
export async function saveRules(rules: Rules) { validateRules(rules); await setDoc(doc(db, "transportSettings", "scoring"), rules); }
export async function excludeAlert(id: string, excluded: boolean) { await updateDoc(doc(db, "alerts", id), { excluded }); }
export async function settleDelivery(id: string) {
  // A stable document ID and transaction prevent duplicate rewards.
  await runTransaction(db, async transaction => {
    const deliveryRef = doc(db, "deliveries", id), scoreRef = doc(db, "deliveryScores", id);
    const [deliverySnap, scoreSnap, ruleSnap] = await Promise.all([transaction.get(deliveryRef), transaction.get(scoreRef), transaction.get(doc(db, "transportSettings", "scoring"))]);
    if (scoreSnap.exists()) throw new Error("Chuyến này đã được chốt điểm.");
    const delivery = deliverySnap.data() as Delivery | undefined;
    if (!delivery || delivery.status !== "completed" || !delivery.completedAt || !delivery.stops.length || delivery.stops.some(stop => stop.status !== "delivered")) throw new Error("Chỉ chốt điểm chuyến đã giao thành công tất cả các điểm.");
    const rules = { ...defaultRules, ...ruleSnap.data() }; validateRules(rules);
    // Reviewed violation count is provided by the server; never trust a browser count.
    const reviewed = await transaction.get(doc(db, "deliveryReviews", id));
    const review = reviewed.data();
    if (!review || review.status !== "final" || !Number.isSafeInteger(review.violations) || review.violations < 0) throw new Error("Chuyến chưa được dịch vụ máy chủ xác nhận số vi phạm.");
    transaction.set(scoreRef, { deliveryId: id, userId: delivery.userId, violations: review.violations, points: pointsFor(review.violations, rules), rules, completedAt: millis(delivery.completedAt), createdAt: Date.now() });
  });
}
