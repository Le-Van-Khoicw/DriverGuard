import { useEffect, useState } from 'react';

export default function RequestNotice({ active, label }: { active: boolean; label: string }) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    setSeconds(0);
    if (!active) return;
    const started = Date.now();
    const timer = window.setInterval(() => setSeconds(Math.floor((Date.now() - started) / 1000)), 1000);
    return () => window.clearInterval(timer);
  }, [active]);
  if (!active) return null;
  return <aside className="error-notice request-notice" role="status">
    <div><strong>{label}</strong><p>{seconds < 10 ? 'Đang chờ máy chủ xác nhận…' : 'Kết nối đang chậm hoặc máy chủ đang khởi động. Vui lòng chờ, không cần bấm lại.'} ({seconds}s)</p></div>
  </aside>;
}
