param([string]$ApiUrl = '')
$ErrorActionPreference = 'Stop'
if ($ApiUrl) { & (Join-Path $PSScriptRoot 'Configure-Online.ps1') -ApiUrl $ApiUrl }
$androidPath = Join-Path $PSScriptRoot 'Mobile\DriverGuard'
if (-not (Test-Path -LiteralPath (Join-Path $androidPath 'online.properties'))) { throw 'Chay Configure-Online.ps1 voi URL Render truoc.' }
Push-Location $androidPath
try {
    & .\gradlew.bat :app:assembleDebug -PfirebaseEmulators=false --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Khong dung duoc APK online' }
    $destination = Join-Path $androidPath 'app\build\outputs\online'
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    $apk = Join-Path $destination 'DriverGuard-online.apk'
    Copy-Item -LiteralPath (Join-Path $androidPath 'app\build\outputs\apk\debug\app-debug.apk') -Destination $apk -Force
    Write-Host "APK Firebase that: $apk"
} finally { Pop-Location }
