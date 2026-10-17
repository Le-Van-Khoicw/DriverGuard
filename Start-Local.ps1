$ErrorActionPreference = 'Stop'
$workspacePath = $PSScriptRoot
Push-Location (Join-Path $workspacePath 'Firebase')
try {
    & npm.cmd start
} finally {
    Pop-Location
}
