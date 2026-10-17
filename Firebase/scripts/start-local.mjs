import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createConnection } from 'node:net';

const directory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const web = resolve(directory, '../Web');
const firebase = resolve(directory, 'node_modules/firebase-tools/lib/bin/firebase.js');
const vite = resolve(web, 'node_modules/vite/bin/vite.js');
if (![firebase, vite, resolve(directory, 'functions/node_modules/firebase-admin')].every(existsSync)) {
  throw new Error('Cài npm ci trong Firebase, Firebase/functions và Web trước khi chạy.');
}
for (const port of [4000, 4400, 5001, 8080, 9099, 9199, 5173]) {
  const occupied = await new Promise(resolveResult => {
    const connection = createConnection({ host: '127.0.0.1', port });
    connection.once('connect', () => { connection.destroy(); resolveResult(true); });
    connection.once('error', () => resolveResult(false));
  });
  if (occupied) throw new Error(`Cổng ${port} đang được dùng. Dừng bộ local cũ bằng Stop-Local.ps1 trước khi khởi động lại.`);
}
mkdirSync(resolve(directory, '.local-runtime'), { recursive: true });
writeFileSync(resolve(directory, '.local-runtime/process.json'), JSON.stringify({ rootPid: process.pid, startedAt: Date.now() }));
const children = new Set();
let stopping = false;
function launch(file, args, cwd, env = {}) {
  const child = spawn(process.execPath, [file, ...args], { cwd, stdio: 'inherit', env: { ...process.env, ...env }, windowsHide: true });
  children.add(child);
  child.on('error', e => { console.error(e.message); shutdown(1); });
  child.on('exit', code => { children.delete(child); if (!stopping) shutdown(code || 0); });
  return child;
}
async function shutdown(code = 0) {
  if (stopping) return;
  stopping = true;
  console.log('\nĐang dừng máy chủ local. Firebase sẽ lưu dữ liệu vào .emulator-data.');
  // Windows does not deliver child.kill('SIGINT') as a console Ctrl+C.
  // Explicitly export first, then stop the entire service process trees.
  try {
    await new Promise(resolveExit => {
      const exporter = spawn(process.execPath, [firebase, 'emulators:export', '.emulator-data', '--project', 'demo-driverguard', '--force'],
        { cwd: directory, stdio: 'inherit', windowsHide: true });
      exporter.on('error', () => resolveExit());
      exporter.on('exit', () => resolveExit());
    });
  } finally {
    for (const child of children) {
      if (process.platform === 'win32' && child.pid) spawn('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true });
      else child.kill('SIGINT');
    }
  }
  process.exitCode = code;
}
process.on('SIGINT', () => shutdown());
process.on('SIGTERM', () => shutdown());
const emulatorArgs = ['emulators:start', '--project', 'demo-driverguard', '--only', 'auth,firestore,functions,storage', '--export-on-exit', '.emulator-data'];
if (existsSync(resolve(directory, '.emulator-data/firebase-export-metadata.json'))) emulatorArgs.push('--import', '.emulator-data');
launch(firebase, emulatorArgs, directory);

const deadline = Date.now() + 180_000;
let ready = false;
while (!stopping && Date.now() < deadline) {
  try {
    const response = await fetch('http://127.0.0.1:4400/emulators');
    const services = await response.json();
    ready = ['auth', 'firestore', 'functions', 'storage'].every(name => services[name]);
    if (ready) break;
  } catch {}
  await new Promise(r => setTimeout(r, 1000));
}
if (!ready && !stopping) { console.error('Chưa khởi động được emulator. Xem lỗi phía trên.'); shutdown(1); }
if (ready && !stopping) {
  // Use a one-shot seed process without the service shutdown handler.
  const seeded = await new Promise(resolveExit => {
    const child = spawn(process.execPath, [resolve(directory, 'scripts/seed-local.mjs')], { cwd: directory, stdio: 'inherit', windowsHide: true });
    child.on('error', () => resolveExit(false)); child.on('exit', code => resolveExit(code === 0));
  });
  if (!seeded) { console.error('Không tạo được tài khoản local.'); shutdown(1); }
  else {
    // A dedicated Vite mode leaves the user's ordinary .env.local untouched.
    const modeFile = resolve(web, '.env.thesis.local');
    const mode = 'VITE_FIREBASE_EMULATORS=true\nVITE_FIREBASE_PROJECT_ID=demo-driverguard\n';
    if (!existsSync(modeFile) || readFileSync(modeFile, 'utf8') !== mode) writeFileSync(modeFile, mode);
    mkdirSync(resolve(directory, '.local-runtime'), { recursive: true });
    launch(vite, ['--mode', 'thesis', '--host', '127.0.0.1', '--port', '5173', '--strictPort'], web,
      { VITE_FIREBASE_EMULATORS: 'true', VITE_FIREBASE_PROJECT_ID: 'demo-driverguard' });
    console.log('\nWeb: http://127.0.0.1:5173 · Emulator UI: http://127.0.0.1:4000');
    console.log('Dừng bằng Ctrl+C. Không dùng lệnh firebase deploy cho chế độ này.');
  }
}
