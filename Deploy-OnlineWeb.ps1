$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot 'Web\.env.production.local'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'Chạy Configure-Online.ps1 -ApiUrl https://...onrender.com trước.' }
Push-Location (Join-Path $PSScriptRoot 'Web')
try {
    # Firebase CLI requires Hosting assets to stay inside its project directory.
    & npm.cmd run build -- --outDir ../Firebase/hosting-dist
    if ($LASTEXITCODE -ne 0) { throw 'Build web thất bại; chưa triển khai.' }
} finally { Pop-Location }
Push-Location (Join-Path $PSScriptRoot 'Firebase')
try {
    & node node_modules/firebase-tools/lib/bin/firebase.js deploy --only hosting --project driverguard-854e5
    if ($LASTEXITCODE -ne 0) { throw 'Triển khai Hosting thất bại' }
} finally { Pop-Location }
