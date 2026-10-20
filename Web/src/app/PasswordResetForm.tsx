import { FormEvent, useState } from 'react';
import { sendPasswordResetEmail } from 'firebase/auth';
import { auth } from '../api/firebase';
import { loginErrorMessage } from '../api/authErrors';

export default function PasswordResetForm({ initialEmail, back }: { initialEmail: string; back: () => void }) {
  const local = import.meta.env.VITE_FIREBASE_EMULATORS === 'true';
  const [email, setEmail] = useState(initialEmail), [busy, setBusy] = useState(false), [requested, setRequested] = useState(false), [error, setError] = useState('');
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setRequested(false); setError('');
    try { auth.languageCode = 'vi'; await sendPasswordResetEmail(auth, email.trim()); setRequested(true); }
    catch (reason) {
      const code = typeof reason === 'object' && reason !== null && 'code' in reason ? String(reason.code) : '';
      // Keep the same confirmation for unknown accounts; do not expose account existence.
      if (code === 'auth/user-not-found') setRequested(true);
      else setError(loginErrorMessage(reason, local));
    } finally { setBusy(false); }
  }
  return <>
    <form className="login-form" onSubmit={submit}>
      <h3>Quên mật khẩu</h3>
      <p className="password-reset-help">{local ? 'Đang đặt lại mật khẩu LOCAL. Thao tác này không đổi mật khẩu tài khoản trên Firebase thật.' : 'Nhập email đã đăng ký để yêu cầu đường dẫn đặt lại mật khẩu.'}</p>
      <label>Email đăng ký<input autoFocus type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} placeholder="VD: tenban@example.com" required /></label>
      {error && <p className="form-error" role="alert">{error}</p>}
      {requested && <div className="success-box" role="status">{local
        ? 'Đã gửi yêu cầu local. Nếu email có tài khoản phù hợp, đường dẫn reset xuất hiện trong terminal chạy Start-Local.ps1, không có thư gửi đến hộp mail. Nhờ người chạy máy chủ mở đường dẫn để hoàn tất.'
        : 'Nếu email có tài khoản phù hợp, hướng dẫn đặt lại mật khẩu sẽ được gửi tới hộp thư. Kiểm tra cả thư rác.'}</div>}
      <button className="primary-button login-button" disabled={busy}>{busy ? 'Đang gửi yêu cầu…' : local ? 'Yêu cầu reset mật khẩu local' : 'Gửi yêu cầu đặt lại mật khẩu'}</button>
      <button type="button" className="secondary-button" onClick={back} disabled={busy}>← Quay lại đăng nhập</button>
    </form>
  </>;
}
