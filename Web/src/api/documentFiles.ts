import { getBlob, ref } from 'firebase/storage';
import { auth, storage } from './firebase';

export async function readDocument(path: string): Promise<Blob> {
  if (import.meta.env.VITE_FIREBASE_EMULATORS === 'true') {
    if (path.startsWith('supabase:')) throw new Error('Giấy tờ online cần mở bằng bản web online.');
    return getBlob(ref(storage, path), 5 * 1024 * 1024);
  }
  const user = auth.currentUser;
  if (!user) throw new Error('Cần đăng nhập để xem giấy tờ.');
  const base = (import.meta.env.VITE_DISPATCH_API_URL || '').replace(/\/+$/, '');
  if (!base.startsWith('https://')) throw new Error('Chưa cấu hình URL backend online.');
  const token = await user.getIdToken();
  const response = await fetch(`${base}/driver-documents?path=${encodeURIComponent(path)}`, {
    headers: { Authorization: `Bearer ${token}` }, cache: 'no-store', signal: AbortSignal.timeout(100_000),
  });
  if (auth.currentUser?.uid !== user.uid) throw new Error('Tài khoản đã thay đổi, hãy mở lại hồ sơ.');
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.error?.message || 'Không đọc được giấy tờ riêng tư.');
  }
  const blob = await response.blob();
  if (blob.size > 5 * 1024 * 1024) throw new Error('Tệp vượt 5 MB.');
  return blob;
}
