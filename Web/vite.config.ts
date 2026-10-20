import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ command, mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env };
  if (command === 'build' && env.VITE_FIREBASE_EMULATORS !== 'true') {
    const url = env.VITE_DISPATCH_API_URL;
    if (!url || !/^https:\/\/[^/]+\/?$/.test(url)) throw new Error('Set VITE_DISPATCH_API_URL to the HTTPS origin of your Render API before building online.');
    if (env.VITE_FIREBASE_PROJECT_ID?.startsWith('demo-')) throw new Error('Online build must use a real Firebase project');
  }
  return { plugins: [react()], server: { port: 5173 } };
})
