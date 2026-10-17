param(
    [switch]$PhoneUsb,
    [switch]$Install,
    [string]$Device = ''
)
$ErrorActionPreference = 'Stop'
$workspacePath = $PSScriptRoot
$androidPath = Join-Path $workspacePath 'Mobile\DriverGuard'
$serverHost = if ($PhoneUsb) { '127.0.0.1' } else { '10.0.2.2' }
Push-Location $androidPath
try {
    & .\gradlew.bat :app:assembleDebug -PfirebaseEmulators=true "-PfirebaseEmulatorHost=$serverHost" --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Không dựng được APK local' }
    $outputDirectory = Join-Path $androidPath 'app\build\outputs\local'
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $apkPath = Join-Path $outputDirectory 'DriverGuard-local.apk'
    Copy-Item -LiteralPath (Join-Path $androidPath 'app\build\outputs\apk\debug\app-debug.apk') -Destination $apkPath -Force
    Write-Host "APK local: $apkPath"
    if ($Install) {
        $adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
        if (-not (Test-Path -LiteralPath $adbPath)) { $adbPath = (Get-Command adb -ErrorAction Stop).Source }
        $deviceArguments = if ($Device) { @('-s', $Device) } else { @() }
        if ($PhoneUsb) {
            foreach ($port in @(9099, 8080, 5001, 9199)) {
                & $adbPath @deviceArguments reverse "tcp:$port" "tcp:$port"
                if ($LASTEXITCODE -ne 0) { throw "Không mở được cổng USB $port" }
            }
        }
        & $adbPath @deviceArguments install -r $apkPath
        if ($LASTEXITCODE -ne 0) { throw 'Không cài được APK local' }
    }
} finally {
    Pop-Location
}
