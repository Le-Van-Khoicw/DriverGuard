import { FormEvent, useEffect, useState } from "react";
import { onAuthStateChanged } from "firebase/auth";
import { doc, onSnapshot } from "firebase/firestore";
import { Activity, AlertTriangle, ChevronRight, Eye, EyeOff, Gauge, LoaderCircle, LockKeyhole, ShieldCheck } from "lucide-react";
import { auth, db } from "../api/firebase";
import { api } from "../api/client";
import { loginErrorMessage } from "../api/authErrors";
import { Staff } from "../api/dispatch";
import LogisticsPortal from "./LogisticsPortal";
import PasswordResetForm from "./PasswordResetForm";

function Badge({ children, tone = "gray" }: { children: React.ReactNode; tone?: "green" | "red" | "amber" | "blue" | "gray" }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

function Login({ onSuccess }: { onSuccess: () => void }) {
  const local = import.meta.env.VITE_FIREBASE_EMULATORS === 'true';
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [resetPassword, setResetPassword] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      await api.login(username.trim(), password);
      onSuccess();
    } catch (reason) {
      setError(loginErrorMessage(reason, local));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-shell">
        <div className="login-brand">
          <div className="brand-lockup"><span className="brand-mark"><ShieldCheck /></span><div><strong>DrowsyGuard</strong><small>ĐIỀU HÀNH VẬN TẢI</small></div></div>
          <div className="brand-copy">
            <Badge tone="green"><span className="live-dot" /> Hệ thống giám sát an toàn</Badge>
            <h1>Điều phối chuyến hàng và theo dõi tài xế đường dài.</h1>
            <p>Quản lý đơn vận chuyển, phân công xe và tài xế, theo dõi kết quả từng điểm giao.</p>
          </div>
          <div className="brand-features">
            <span><Gauge /> Theo dõi trạng thái thiết bị</span>
            <span><Activity /> Thống kê cảnh báo theo thời gian</span>
            <span><ShieldCheck /> Phân quyền quản trị và điều phối</span>
          </div>
        </div>
        <div className="login-form-panel">
          <div className="mobile-brand"><ShieldCheck /> DrowsyGuard</div>
          <div className="login-heading"><p>CỔNG QUẢN TRỊ</p><h2>Đăng nhập hệ thống</h2><span>Sử dụng tài khoản quản trị hoặc điều phối đã được cấp quyền.</span></div>
          {local && <div className="login-environment"><strong>Bản LOCAL · demo-driverguard</strong><p>Tài khoản trên Firebase thật không tự đăng nhập được vào bản này. Dùng tài khoản local đã tạo; tài khoản tài xế đăng nhập trên app.</p><small>Quản lý demo: admin@driverguard.test<br />Điều phối demo: dieuphoi@driverguard.test</small></div>}
          {resetPassword ? <PasswordResetForm initialEmail={username} back={() => setResetPassword(false)} /> : <form onSubmit={submit} className="login-form">
            <label>Email nhân viên<input autoFocus type="email" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Email nhân viên" required /></label>
            <label>Mật khẩu<div className="password-field"><input value={password} onChange={(e) => setPassword(e.target.value)} type={showPassword ? "text" : "password"} placeholder="Nhập mật khẩu" required /><button type="button" aria-label="Hiện hoặc ẩn mật khẩu" onClick={() => setShowPassword((v) => !v)}>{showPassword ? <EyeOff /> : <Eye />}</button></div></label>
            {error && <div className="form-error"><AlertTriangle />{error}</div>}
            <button className="primary-button login-button" disabled={loading}>{loading ? <><LoaderCircle className="spin" />Đang xác thực</> : <>Đăng nhập<ChevronRight /></>}</button>
            <button type="button" className="text-button forgot-password-link" disabled={loading} onClick={() => { setError(''); setResetPassword(true); }}>Quên mật khẩu?</button>
          </form>}
          <div className="login-note"><LockKeyhole /> Dành cho tài khoản quản trị và điều phối viên được cấp quyền.</div>
        </div>
      </section>
    </main>
  );
}

export default function App() {
  const [userId, setUserId] = useState<string | null>(null);
  const [staff, setStaff] = useState<Staff | null>(null);
  const [checking, setChecking] = useState(true);
  const [accessError, setAccessError] = useState("");
  useEffect(() => {
    let unsubscribeStaff = () => {};
    const unsubscribeAuth = onAuthStateChanged(auth, user => {
      unsubscribeStaff(); setStaff(null); setUserId(user?.uid || null); setAccessError("");
      if (!user) { setChecking(false); return; }
      setChecking(true);
      unsubscribeStaff = onSnapshot(doc(db, "staff", user.uid), snapshot => {
        const data = snapshot.data();
        if (data?.active === true && ["admin", "dispatcher"].includes(data.role)) setStaff({ ...data, uid: user.uid } as Staff);
        else { setStaff(null); setAccessError("Đăng nhập thành công, nhưng tài khoản chưa có quyền admin/điều phối đang hoạt động. Nếu đây là tài khoản tài xế, hãy dùng app Android; nhân viên cần được quản lý cấp quyền theo UID."); }
        setChecking(false);
      }, error => { setStaff(null); setAccessError(error.message); setChecking(false); });
    });
    return () => { unsubscribeStaff(); unsubscribeAuth(); };
  }, []);
  if (checking) return <main className="loading-screen">Đang xác thực tài khoản và quyền truy cập…</main>;
  if (!userId) return <Login onSuccess={() => {}} />;
  if (!staff) return <main className="loading-screen"><p role="alert">{accessError}</p><button onClick={() => api.logout()}>Đăng xuất</button></main>;
  return <LogisticsPortal key={staff.uid} staff={staff} logout={() => api.logout()} />;
}
