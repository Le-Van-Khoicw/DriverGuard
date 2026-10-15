export type EmployeeStatus = "pending" | "active" | "locked" | "rejected";

export function employeeStatus(data: Record<string, unknown>): EmployeeStatus {
  if (data.accountStatus === "pending" || data.accountStatus === "active" || data.accountStatus === "locked" || data.accountStatus === "rejected") return data.accountStatus;
  // Preserve legacy accounts while respecting the old lock field.
  if (data.isActive === false || data.is_active === false) return "locked";
  return "active";
}

export function employeeStatusUpdate(status: EmployeeStatus, reason?: string) {
  if (!["pending", "active", "locked", "rejected"].includes(status)) throw new Error("Trạng thái nhân viên không hợp lệ");
  if (status === "rejected" && !reason?.trim()) throw new Error("Vui lòng nhập lý do từ chối");
  return { rejectReason: status === "rejected" ? reason!.trim() : "", accountStatus: status, isActive: status === "active", is_active: status === "active" };
}

