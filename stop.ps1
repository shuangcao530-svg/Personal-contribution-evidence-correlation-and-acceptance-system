$ErrorActionPreference = 'Stop'
$statePath = Join-Path $PSScriptRoot '.runtime\server.json'
if (-not (Test-Path -LiteralPath $statePath)) {
    Write-Host 'No server started by this project was recorded.'
    exit 0
}
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
$expectedJarPath = Join-Path $PSScriptRoot 'backend\target\contribution-evidence-0.1.0.jar'
if ([IO.Path]::GetFullPath($state.jarPath) -ne [IO.Path]::GetFullPath($expectedJarPath)) {
    throw 'Server record does not belong to this project.'
}
$runningProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($state.pid)" -ErrorAction Stop
if ($runningProcess) {
    if (-not $runningProcess.CommandLine -or
        $runningProcess.CommandLine.Replace('\\', '\').IndexOf($expectedJarPath, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw 'PID now belongs to another process. No process was stopped.'
    }
    $verifiedProcess = [Diagnostics.Process]::GetProcessById($state.pid)
    $verifiedProcess.Kill()
    if (-not $verifiedProcess.WaitForExit(10000)) { throw 'The server process has not exited yet.' }
    Write-Host 'Application stopped. Saved project data and uploads have been kept.'
} else { Write-Host 'Application is already stopped.' }
Remove-Item -LiteralPath $statePath
