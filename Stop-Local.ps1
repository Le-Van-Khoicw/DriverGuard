$ErrorActionPreference = 'Stop'
$firebasePath = Join-Path $PSScriptRoot 'Firebase'
$processFile = Join-Path $firebasePath '.local-runtime\process.json'
if (-not (Test-Path -LiteralPath $processFile)) { throw 'Không có thông tin máy chủ local đang chạy.' }
$localProcess = Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json
$processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$localProcess.rootPid)"
if (-not $processInfo -or $processInfo.CommandLine -notmatch 'scripts[\\/]start-local\.mjs') {
    throw 'Máy chủ local không còn chạy, hoặc PID thuộc chương trình khác. Không dừng tiến trình đó.'
}
Push-Location $firebasePath
try {
    & node node_modules/firebase-tools/lib/bin/firebase.js emulators:export .emulator-data --project demo-driverguard --force
    if ($LASTEXITCODE -ne 0) { throw 'Chưa xuất được dữ liệu; máy chủ được giữ chạy để mày xử lý.' }
    & taskkill.exe /PID ([string]$localProcess.rootPid) /T /F
    if ($LASTEXITCODE -ne 0) { throw 'Đã xuất dữ liệu nhưng chưa dừng được toàn bộ tiến trình.' }
    Write-Host 'Đã lưu dữ liệu và dừng máy chủ local.'
} finally {
    Pop-Location
}
