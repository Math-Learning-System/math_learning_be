# Creates local PostgreSQL database for Math Master (pgAdmin / native Postgres).
# Run once after installing PostgreSQL. Requires psql in PATH.

param(
    [string]$DbName = "math_learning",
    [string]$DbUser = "math_learning",
    [string]$DbPassword = "MathLearning@2026",
    [string]$PostgresHost = "localhost",
    [int]$Port = 5432,
    [string]$SuperUser = "postgres",
    [switch]$Help
)

function Show-Help {
    Write-Host ""
    Write-Host "Setup Local PostgreSQL Database" -ForegroundColor Cyan
    Write-Host "  .\setup-local-db.ps1" -ForegroundColor White
    Write-Host ""
}

if ($Help) { Show-Help; exit 0 }

$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    Write-Host "ERROR: psql not found. Add PostgreSQL bin to PATH." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Math Master - Local PostgreSQL Setup ===" -ForegroundColor Cyan
Write-Host "  Database: $DbName  |  User: $DbUser" -ForegroundColor White
Write-Host ""

$escapedPassword = $DbPassword -replace "'", "''"

function Invoke-PsqlAdmin([string]$Command) {
    & psql -h $PostgresHost -p $Port -U $SuperUser -d postgres -v ON_ERROR_STOP=1 -c $Command
    return $LASTEXITCODE
}

Write-Host "Connecting as $SuperUser (enter password if prompted)..." -ForegroundColor Yellow

$roleExists = & psql -h $PostgresHost -p $Port -U $SuperUser -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$($DbUser)'" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Connection failed. Check PostgreSQL service and superuser password." -ForegroundColor Red
    exit 1
}

if ($roleExists -match "1") {
    $null = Invoke-PsqlAdmin "ALTER ROLE $DbUser WITH LOGIN PASSWORD '$escapedPassword';"
} else {
    $null = Invoke-PsqlAdmin "CREATE ROLE $DbUser LOGIN PASSWORD '$escapedPassword';"
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$dbExists = & psql -h $PostgresHost -p $Port -U $SuperUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$($DbName)'" 2>&1
if ($dbExists -match "1") {
    Write-Host "Database $DbName already exists." -ForegroundColor Yellow
} else {
    $null = Invoke-PsqlAdmin "CREATE DATABASE $DbName OWNER $DbUser ENCODING UTF8;"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$grantSql = "GRANT ALL ON SCHEMA public TO $DbUser; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO $DbUser; ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO $DbUser;"
& psql -h $PostgresHost -p $Port -U $SuperUser -d $DbName -v ON_ERROR_STOP=1 -c $grantSql | Out-Null

Write-Host ""
Write-Host "OK  Database ready." -ForegroundColor Green
Write-Host "  pgAdmin: localhost:$Port / $DbName / $DbUser / $DbPassword" -ForegroundColor White
Write-Host "  Copy .env.local.example to .env.local then start the app." -ForegroundColor Yellow
Write-Host ""
