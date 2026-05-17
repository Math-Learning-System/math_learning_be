# Backup PostgreSQL database (local or any reachable host).
# Use before prod deploy / to copy prod data to local.

param(
    [string]$DbName = "math_learning",
    [string]$DbUser = "math_learning",
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$OutputDir = "",
    [switch]$SchemaOnly,
    [switch]$Help
)

function Show-Help {
    Write-Host ""
    Write-Host "Backup database to SQL file (pg_dump)." -ForegroundColor Cyan
    Write-Host "  .\db-backup.ps1 [-SchemaOnly]" -ForegroundColor White
    Write-Host "  `$env:PGPASSWORD='...'; .\db-backup.ps1 -DbHost prod-host -DbName neondb" -ForegroundColor Gray
    Write-Host ""
}

if ($Help) { Show-Help; exit 0 }

$pgDump = Get-Command pg_dump -ErrorAction SilentlyContinue
if (-not $pgDump) {
    Write-Host "ERROR: pg_dump not in PATH." -ForegroundColor Red
    exit 1
}

$root = Split-Path (Split-Path $PSScriptRoot)
if (-not $OutputDir) {
    $OutputDir = Join-Path $root "backups"
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$suffix = if ($SchemaOnly) { "schema" } else { "full" }
$outFile = Join-Path $OutputDir "${DbName}_${suffix}_${timestamp}.sql"

$args = @(
    "-h", $DbHost,
    "-p", "$Port",
    "-U", $DbUser,
    "-d", $DbName,
    "-F", "p",
    "--no-owner",
    "--no-acl",
    "-f", $outFile
)
if ($SchemaOnly) { $args += "--schema-only" }

Write-Host "Dumping $DbName @ ${DbHost}:${Port} -> $outFile" -ForegroundColor Cyan
& pg_dump @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "OK  Backup saved: $outFile" -ForegroundColor Green
