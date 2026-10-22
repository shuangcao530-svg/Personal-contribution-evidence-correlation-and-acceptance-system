param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$runtimePath = Join-Path $projectRoot '.runtime'
$localDatabaseSettings = Join-Path $runtimePath 'local-database.properties'
if (Test-Path -LiteralPath $localDatabaseSettings) {
    $databaseSettings = Get-Content -LiteralPath $localDatabaseSettings -Raw | ConvertFrom-StringData
    if (-not $env:DB_USER -and $databaseSettings['DB_USER']) { $env:DB_USER = $databaseSettings['DB_USER'] }
    if (-not $env:DB_PASSWORD -and $databaseSettings['DB_PASSWORD']) { $env:DB_PASSWORD = $databaseSettings['DB_PASSWORD'] }
}
$backendPath = Join-Path $projectRoot 'backend'
$jarPath = Join-Path $backendPath 'target\contribution-evidence-0.1.0.jar'
$applicationUrl = 'http://127.0.0.1:18080'

try {
    $health = Invoke-RestMethod -Uri "$applicationUrl/api/health" -TimeoutSec 2
    if ($health.app -eq 'contribution-evidence' -and $health.status -eq 'UP') {
        Write-Host "Application is already running: $applicationUrl"
        if (-not $NoBrowser) { Start-Process $applicationUrl }
        exit 0
    }
} catch { }

if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Packaged application not found. Run build.ps1 first.' }
$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
if (-not $javaCommand) { throw 'Java 21 is required. Please install JDK 21 and retry.' }
$javaExecutable = $javaCommand.Source
$previousErrorPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $javaSettings = & $javaExecutable -XshowSettings:properties -version 2>&1
} finally { $ErrorActionPreference = $previousErrorPreference }
foreach ($settingLine in $javaSettings) {
    if ([string]$settingLine -match '^\s*java.home\s*=\s*(.+)$') {
        $resolvedJava = Join-Path $Matches[1].Trim() 'bin\java.exe'
        if (Test-Path -LiteralPath $resolvedJava) { $javaExecutable = $resolvedJava }
    }
}
$occupiedPort = Get-NetTCPConnection -LocalPort 18080 -State Listen -ErrorAction SilentlyContinue
if ($occupiedPort) { throw 'Port 18080 is occupied by another application. Stop that application first.' }
New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null
$serverProcess = Start-Process -FilePath $javaExecutable `
    -ArgumentList @('-jar', ('"' + $jarPath + '"')) `
    -WorkingDirectory $backendPath -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $runtimePath 'server.out.log') `
    -RedirectStandardError (Join-Path $runtimePath 'server.err.log')
@{ pid = $serverProcess.Id; jarPath = $jarPath; startedAt = (Get-Date).ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimePath 'server.json') -Encoding UTF8
Write-Host 'Starting the application...'
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    if ($serverProcess.HasExited) {
        Get-Content -LiteralPath (Join-Path $runtimePath 'server.err.log') -Tail 25
        throw 'Server stopped during startup. Check .runtime/server.out.log and server.err.log.'
    }
    try {
        $health = Invoke-RestMethod -Uri "$applicationUrl/api/health" -TimeoutSec 1
        if ($health.app -eq 'contribution-evidence' -and $health.status -eq 'UP') {
            Write-Host "Ready: $applicationUrl" -ForegroundColor Green
            Write-Host 'Demo accounts: teacher / student / student2 / student3; password: demo1234'
            if (-not $NoBrowser) { Start-Process $applicationUrl }
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 1
    $serverProcess.Refresh()
}
throw 'Startup timed out. Check .runtime/server.out.log and server.err.log.'


