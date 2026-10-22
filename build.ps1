param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
. (Join-Path $projectRoot 'scripts\Find-Maven.ps1')
$mavenExecutable = Find-ProjectMaven
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js 22.12+ or 24+ is required.' }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java 21 is required.' }

Push-Location (Join-Path $projectRoot 'frontend')
try {
    & npm.cmd ci --cache (Join-Path $projectRoot '.runtime\npm-cache') --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'Installing frontend dependencies failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }

Push-Location (Join-Path $projectRoot 'backend')
try {
    $buildArguments = @('package', '-B')
    $mavenRepository = Join-Path $env:USERPROFILE '.m2\repository'
    if (Test-Path -LiteralPath $mavenRepository) { $buildArguments += "-Dmaven.repo.local=$mavenRepository" }
    if ($SkipTests) { $buildArguments += '-DskipTests' }
    & $mavenExecutable @buildArguments
    if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed.' }
} finally { Pop-Location }
Write-Host 'Build finished. Run start.ps1 to open the application.' -ForegroundColor Green

