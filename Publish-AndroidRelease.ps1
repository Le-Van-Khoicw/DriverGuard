param([string]$Version = '1.0.2', [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
if (-not $SkipBuild) { & (Join-Path $PSScriptRoot 'Build-OnlineAndroid.ps1') }
& python (Join-Path $PSScriptRoot 'scripts\publish_android_release.py') $Version
if ($LASTEXITCODE -ne 0) { throw 'Android release publication failed' }
