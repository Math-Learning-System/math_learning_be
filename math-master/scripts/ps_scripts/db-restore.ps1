# Restore a pg_dump SQL file into local math_learning database.
# WARNING: drops and recreates objects in the dump (use on local/dev only).

param(
    [Parameter(Mandatory = $true)]
    [string]$SqlFile,
    [string]$DbName = "math_learning",
    [string]$DbUser = "math_learning",
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [switch]$Help
)

function Show-Help {
    Write-Host ""
    Write-Host "Restore SQL dump into local database." -ForegroundColor Cyan
    Write-Host "  .\db-restore.ps1 -SqlFile ..\..\backups\math_learning_full_20260101.sql" -ForegroundColor White
    Write-Host ""
}

if ($Help) { Show-Help; exit 0 }

if (-not (Test-Path $SqlFile)) {
    Write-Host "ERROR: File not found: $SqlFile" -ForegroundColor Red
    exit 1
}

$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    Write-Host "ERROR: psql not in PATH." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Restoring into ${DbHost}:${Port}/$DbName" -ForegroundColor Yellow
Write-Host "File: $SqlFile" -ForegroundColor White
$confirm = Read-Host "Continue? This may overwrite existing data (yes/no)"
if ($confirm -ne "yes") {
    Write-Host "Cancelled." -ForegroundColor Gray
    exit 0
}

& psql -h $DbHost -p $Port -U $DbUser -d $DbName -f $SqlFile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "OK  Restore finished." -ForegroundColor Green
Write-Host "If this dump came from production, set flyway baseline in prod or run: mvn flyway:info" -ForegroundColor Gray
