param([Parameter(Mandatory = $true)][string]$ApiUrl)
$ErrorActionPreference = 'Stop'
$ApiUrl = $ApiUrl.TrimEnd('/')
if ($ApiUrl -notmatch '^https://[A-Za-z0-9.-]+(:[0-9]+)?$') { throw 'ApiUrl phải là HTTPS origin của backend Render, không có đường dẫn.' }
if ($ApiUrl -match '\.(invalid|example|test)(:|$)') { throw 'Cần URL triển khai thực tế, không dùng URL ví dụ.' }
$webConfig = @"
VITE_FIREBASE_EMULATORS=false
VITE_FIREBASE_PROJECT_ID=driverguard-854e5
VITE_FIREBASE_STORAGE_BUCKET=driverguard-854e5.firebasestorage.app
VITE_DISPATCH_API_URL=$ApiUrl
"@
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'Web\.env.production.local'), $webConfig, $utf8NoBom)
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'Mobile\DriverGuard\online.properties'), "dispatchApiUrl=$ApiUrl`n", $utf8NoBom)
Write-Host 'Đã cấu hình web production và Android dùng Firebase thật + backend Render.'
Write-Host 'Tiếp theo: Start-Online.ps1 hoặc build web; Build-OnlineAndroid.ps1 để tạo APK online.'
