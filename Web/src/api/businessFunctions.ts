import { connectFunctionsEmulator, getFunctions, httpsCallable, httpsCallableFromURL } from 'firebase/functions';
import { app } from './firebase';

const local = import.meta.env.VITE_FIREBASE_EMULATORS === 'true';
const functions = getFunctions(app, 'asia-southeast1');
if (local) connectFunctionsEmulator(functions, '127.0.0.1', 5001);
const base = (import.meta.env.VITE_DISPATCH_API_URL || '').replace(/\/+$/, '');

export async function businessCall<Request, Response>(name: string, data: Request): Promise<Response> {
  if (local) return (await httpsCallable<Request, Response>(functions, name)(data)).data;
  if (!base) throw new Error('Chưa cấu hình URL backend online. Điền VITE_DISPATCH_API_URL rồi dựng lại web.');
  if (new URL(base).protocol !== 'https:') throw new Error('Backend online phải dùng HTTPS');
  // Firebase SDK attaches the signed-in user's ID token to the Render request.
  return (await httpsCallableFromURL<Request, Response>(functions, `${base}/${name}`, { timeout: 90_000 })(data)).data;
}
