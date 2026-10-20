import { initializeApp, getApps, getApp } from "firebase/app";
import { getAuth, connectAuthEmulator } from "firebase/auth";
import { getFirestore, connectFirestoreEmulator } from "firebase/firestore";
import { getStorage, connectStorageEmulator } from "firebase/storage";

const localFirebase = import.meta.env.VITE_FIREBASE_EMULATORS === "true";
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyC1ELFd6TsuGSGNXl7IxYjCdJ8bb_eBVnc",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "driverguard-854e5.firebaseapp.com",
  projectId: localFirebase ? "demo-driverguard" : import.meta.env.VITE_FIREBASE_PROJECT_ID || "driverguard-854e5",
  storageBucket: localFirebase ? "demo-driverguard.appspot.com" : import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "driverguard-854e5.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "925387921691",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:925387921691:web:6f90e598e95e44b0039bbe"
};

export const app = !getApps().length ? initializeApp(firebaseConfig) : getApp();
export const auth = getAuth(app);
export const db = getFirestore(app);
export const storage = getStorage(app);

if (import.meta.env.VITE_FIREBASE_EMULATORS === "true") {
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  connectStorageEmulator(storage, "127.0.0.1", 9199);
}
