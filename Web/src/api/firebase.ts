import { initializeApp, getApps, getApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyB7mjSzK0waZCllvmmCuyQugqjlnMHbL2Q",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "driverguard-854e5.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "driverguard-854e5",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "driverguard-854e5.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "925387921691",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:925387921691:web:driverguard"
};

export const app = !getApps().length ? initializeApp(firebaseConfig) : getApp();
export const auth = getAuth(app);
export const db = getFirestore(app);
