export function loginErrorMessage(error: unknown, local: boolean) {
  const code = typeof error === 'object' && error !== null && 'code' in error ? String(error.code) : '';
  if (['auth/invalid-credential', 'auth/wrong-password', 'auth/user-not-found', 'auth/invalid-login-credentials'].includes(code)) {
    return local
      ? 'Email hoặc mật khẩu không khớp tài khoản LOCAL. Email trên Firebase thật không tự có trong demo-driverguard; dùng tài khoản local đã được tạo và cấp quyền.'
      : 'Email hoặc mật khẩu không chính xác. Nếu tài khoản dùng Google, cần dùng đúng phương thức đăng nhập đã liên kết.';
  }
  if (code === 'auth/invalid-email') return 'Địa chỉ email không hợp lệ.';
  if (code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request') return 'Đã đóng cửa sổ Google. Bấm Đăng nhập bằng Google để thử lại.';
  if (code === 'auth/popup-blocked') return 'Trình duyệt đang chặn cửa sổ đăng nhập. Cho phép popup của trang này rồi thử lại.';
  if (code === 'auth/unauthorized-domain') return 'Tên miền web chưa được cho phép trong Firebase Authentication → Settings → Authorized domains.';
  if (code === 'auth/operation-not-allowed') return 'Phương thức đăng nhập chưa được bật trong Firebase Authentication → Sign-in method.';
  if (code === 'auth/account-exists-with-different-credential') return 'Email đã dùng một phương thức đăng nhập khác. Đăng nhập bằng phương thức đã liên kết; không tạo lại tài khoản để tránh mất liên kết UID.';
  if (code === 'auth/user-disabled') return 'Tài khoản đã bị vô hiệu hóa trong Authentication.';
  if (code === 'auth/too-many-requests') return 'Đã thử quá nhiều lần. Đợi một lúc trước khi đăng nhập lại.';
  if (code === 'auth/network-request-failed') return local
    ? 'Không kết nối được máy chủ đăng nhập local. Kiểm tra Start-Local.ps1 đang chạy.'
    : 'Không kết nối được dịch vụ đăng nhập. Kiểm tra mạng.';
  return error instanceof Error ? error.message : 'Đăng nhập thất bại.';
}
