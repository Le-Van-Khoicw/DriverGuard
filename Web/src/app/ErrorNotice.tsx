export default function ErrorNotice({ message, dismiss }: { message: string; dismiss: () => void }) {
  if (!message) return null;
  return <aside className="error-notice" role="alert" aria-live="assertive">
    <div><strong>Chưa thực hiện được</strong><p>{message}</p></div>
    <button type="button" onClick={dismiss} aria-label="Đóng thông báo lỗi">×</button>
  </aside>;
}
