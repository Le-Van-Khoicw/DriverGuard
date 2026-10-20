$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'Web\.env.production.local'))) { throw 'Chạy Configure-Online.ps1 -ApiUrl https://...onrender.com trước.' }
Push-Location (Join-Path $PSScriptRoot 'Web')
try {
    # Production mode loads the real-project config, even when viewing web on localhost.
    & npm.cmd run dev -- --mode production --host 127.0.0.1 --port 5173 --strictPort
    if ($LASTEXITCODE -ne 0) { throw 'Không khởi động được web online' }
} finally { Pop-Location }
