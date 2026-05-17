# Math Master Manager - Windows PowerShell (local PostgreSQL dev)

param(
    [ValidateSet(
        'ApplyFormat', 'SortAnnotations', 'CleanBuild', 'CleanBuildStart',
        'StartRedis', 'StopRedis', 'RestartRedis',
        'StartInfra', 'StopInfra', 'RestartInfra',
        'DeployLocal', 'DeployFull', 'RecreateDocker',
        'Logs', 'LogsRedis', 'LogsApp',
        'Status', 'DeleteLogs', 'SetupLocalDb', 'Help'
    )]
    [string]$Task,
    [switch]$Help,
    [string]$Service = ""
)

$script:InfraServices = @("redis", "centrifugo", "minio", "nginx-minio", "minio-init")

# Cached once at load — Set-Alias to this script may leave $PSScriptRoot empty in functions.
$script:MathMasterRoot = $null

function Initialize-MathMasterRoot {
    if ($script:MathMasterRoot -and (Test-Path (Join-Path $script:MathMasterRoot "mvnw.cmd"))) {
        return $script:MathMasterRoot
    }

    $startDirs = @()
    if ($PSScriptRoot) { $startDirs += $PSScriptRoot }
    $cmdPath = $MyInvocation.MyCommand.Path
    if ($cmdPath) { $startDirs += (Split-Path -Parent $cmdPath) }

    foreach ($start in ($startDirs | Select-Object -Unique)) {
        $dir = $start
        for ($i = 0; $i -lt 8; $i++) {
            $mvnw = Join-Path $dir "mvnw.cmd"
            if (Test-Path $mvnw) {
                $script:MathMasterRoot = (Resolve-Path $dir).Path
                return $script:MathMasterRoot
            }
            $parent = Split-Path $dir -Parent
            if (-not $parent -or $parent -eq $dir) { break }
            $dir = $parent
        }
    }

    $cwd = (Get-Location).Path
    foreach ($candidate in @($cwd, (Join-Path $cwd "math-master"))) {
        if (Test-Path (Join-Path $candidate "mvnw.cmd")) {
            $script:MathMasterRoot = (Resolve-Path $candidate).Path
            return $script:MathMasterRoot
        }
    }

    return $null
}

function Get-ProjectRoot {
    $root = Initialize-MathMasterRoot
    if ($root) { return $root }
    Write-Err "Cannot find math-master (mvnw.cmd). cd to math-master or math_learning_be."
    return (Get-Location).Path
}

$null = Initialize-MathMasterRoot

function Import-ProjectEnvFile {
    param([string]$Root)
    $loaded = @()
    foreach ($name in @(".env", ".env.local")) {
        $path = Join-Path $Root $name
        if (-not (Test-Path $path)) { continue }
        Get-Content $path | ForEach-Object {
            if ($_ -match '^\s*([^#][^=]*)\s*=\s*(.*)$') {
                $key = $matches[1].Trim()
                $val = $matches[2].Trim()
                Set-Item -Path "env:$key" -Value $val
            }
        }
        $loaded += $name
    }
    if ($loaded.Count -eq 0) { return $null }
    return ($loaded -join ", ")
}

function Test-UseLocalDatabase {
    param([string]$Root)
    Import-ProjectEnvFile $Root | Out-Null
    if ($env:DB_MODE -eq 'local') { return $true }
    if ($env:SPRING_PROFILES_ACTIVE -eq 'local') { return $true }
    if ($env:SPRING_DATASOURCE_URL -match 'localhost|127\.0\.0\.1') { return $true }
    return $true
}

function Test-LocalPostgresReady {
    param([string]$Root)
    Import-ProjectEnvFile $Root | Out-Null

    $port = 5432
    $dbName = "math_learning"
    $dbUser = "math_learning"

    if ($env:SPRING_DATASOURCE_URL -match 'jdbc:postgresql://[^:/]+:(\d+)/([^?]+)') {
        $port = [int]$matches[1]
        $dbName = $matches[2]
    }
    if ($env:SPRING_DATASOURCE_USERNAME) { $dbUser = $env:SPRING_DATASOURCE_USERNAME }

    $tcp = Test-NetConnection -ComputerName localhost -Port $port -WarningAction SilentlyContinue
    if (-not $tcp.TcpTestSucceeded) {
        Write-Err "PostgreSQL is not listening on localhost:$port"
        Write-Host "  Start the PostgreSQL service or create the database (menu [5])." -ForegroundColor Yellow
        return $false
    }

    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psql) {
        Write-Host "WARN: psql not in PATH; skipping DB login check." -ForegroundColor Yellow
        return $true
    }

    $env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
    $check = & psql -h localhost -p $port -U $dbUser -d $dbName -tAc "SELECT 1" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Cannot connect to database '$dbName' as '$dbUser'"
        Write-Host "  Create DB in pgAdmin or run menu [5] Setup Local DB." -ForegroundColor Yellow
        Write-Host "  Details: docs/LOCAL_DATABASE.md" -ForegroundColor Gray
        return $false
    }
    Write-OK "PostgreSQL ready ($dbName @ localhost:$port)"
    return $true
}

function Get-LocalDbConnectionInfo {
    param([string]$Root)
    Import-ProjectEnvFile $Root | Out-Null
    $port = 5432
    $dbName = "math_learning"
    $dbUser = "math_learning"
    $jdbcUrl = $env:SPRING_DATASOURCE_URL
    if (-not $jdbcUrl) { $jdbcUrl = "jdbc:postgresql://localhost:5432/$dbName" }
    if ($jdbcUrl -match 'jdbc:postgresql://[^:/]+:(\d+)/([^?]+)') {
        $port = [int]$matches[1]
        $dbName = $matches[2]
    }
    if ($env:SPRING_DATASOURCE_USERNAME) { $dbUser = $env:SPRING_DATASOURCE_USERNAME }
    return @{
        Port     = $port
        Database = $dbName
        User     = $dbUser
        Password = $env:SPRING_DATASOURCE_PASSWORD
        JdbcUrl  = $jdbcUrl
    }
}

function Test-LocalDbHasApplicationTables {
    param($Conn)
    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psql) { return $null }
    $env:PGPASSWORD = $Conn.Password
    $sql = @"
SELECT COUNT(*)::int FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
AND table_name <> 'flyway_schema_history';
"@
    $count = & psql -h localhost -p $Conn.Port -U $Conn.User -d $Conn.Database -tAc $sql 2>&1
    if ($LASTEXITCODE -ne 0) { return $null }
    return ([int]($count.ToString().Trim()) -gt 0)
}

function Test-LocalFlywayFailedMigration {
    param($Conn)
    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psql) { return 0 }
    $env:PGPASSWORD = $Conn.Password
    $sql = "SELECT COUNT(*)::int FROM flyway_schema_history WHERE success = false;"
    $count = & psql -h localhost -p $Conn.Port -U $Conn.User -d $Conn.Database -tAc $sql 2>&1
    if ($LASTEXITCODE -ne 0) { return 0 }
    return [int]($count.ToString().Trim())
}

function Invoke-LocalFlywayMigrate {
    param([string]$Root)
    $conn = Get-LocalDbConnectionInfo $Root
    $hasTables = Test-LocalDbHasApplicationTables $conn

    if ($hasTables -eq $false) {
        Write-Step "Empty database — Flyway deferred (Hibernate creates base schema on first start)."
        return $true
    }

    if ($hasTables -eq $null) {
        Write-Host "WARN: psql unavailable; Flyway will run when Spring Boot starts." -ForegroundColor Yellow
        return $true
    }

    $flywayProps = @(
        "-Dflyway.url=$($conn.JdbcUrl)",
        "-Dflyway.user=$($conn.User)",
        "-Dflyway.password=$($conn.Password)",
        "-Dflyway.placeholderReplacement=false"
    )

    $failed = Test-LocalFlywayFailedMigration $conn
    if ($failed -gt 0) {
        Write-Step "Flyway: repairing $failed failed migration record(s)..."
        & .\mvnw.cmd -q flyway:repair @flywayProps 2>&1 | Out-Null
    }

    Write-Step "Flyway: applying pending migrations (db/migration)..."
    $flywayArgs = @("-q", "flyway:info", "flyway:migrate") + $flywayProps
    & .\mvnw.cmd @flywayArgs 2>&1 | Where-Object {
        $_ -notmatch "Downloading|Downloaded" -and $_ -notmatch "^\s*$"
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Flyway migrate failed. See output above."
        return $false
    }
    Write-OK "Database schema up to date (Flyway)."
    return $true
}

function Assert-Docker {
    try {
        docker info 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw }
    } catch {
        Write-Host "ERROR: Docker is not running. Start Docker Desktop first." -ForegroundColor Red
        return $false
    }
    return $true
}

function Assert-EnvFile($root) {
    if (-Not (Test-Path (Join-Path $root ".env"))) {
        Write-Host "WARN: .env not found. Copy .env.local.example to .env" -ForegroundColor Yellow
    }
}

function Write-Header($title) {
    Write-Host ""
    Write-Host ("=" * 50) -ForegroundColor DarkCyan
    Write-Host "  $title" -ForegroundColor Cyan
    Write-Host ("=" * 50) -ForegroundColor DarkCyan
    Write-Host ""
}

function Write-Step($msg) { Write-Host ">> $msg" -ForegroundColor Yellow }
function Write-OK($msg)   { Write-Host "OK  $msg" -ForegroundColor Green  }
function Write-Err($msg)  { Write-Host "ERR $msg" -ForegroundColor Red    }

function Stop-AppPort {
    param([int]$Port = 8080)
    $procs = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
             Where-Object { $_.State -eq 'Listen' -or $_.State -eq 'Established' }
    if (-not $procs) { Write-OK "Port $Port is already free."; return }
    $processList = $procs | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($processId in $processList) {
        $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
        $name = if ($proc) { $proc.Name } else { "PID $processId" }
        if ($name -match "postgres|pg_ctl") { continue }
        Write-Step "Killing process on port ${Port}: $name (PID $processId)"
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
    for ($i = 0; $i -lt 10; $i++) {
        Start-Sleep -Seconds 1
        $still = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
                 Where-Object { $_.State -eq 'Listen' -or $_.State -eq 'Established' }
        if (-not $still) { Write-OK "Port $Port is now free."; return }
    }
    Write-Err "Port $Port still in use after 10s. Continuing anyway..."
}

function Invoke-StartInfra {
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot
    Import-ProjectEnvFile $root | Out-Null
    Push-Location $root
    Write-Step "Starting Docker infra (redis, minio, centrifugo) — not postgres (use pgAdmin)..."
    docker compose up -d @script:InfraServices
    if ($LASTEXITCODE -eq 0) { Write-OK "Infrastructure started."; docker compose ps }
    else { Write-Err "Failed to start infrastructure." }
    Pop-Location
}

function Invoke-StopInfra {
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot
    Push-Location $root
    Write-Step "Stopping infrastructure containers..."
    docker compose stop @script:InfraServices 2>&1 | Out-Null
    Write-OK "Infrastructure stopped."
    Pop-Location
}

function Invoke-RestartInfra {
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot
    Push-Location $root
    docker compose restart @script:InfraServices
    if ($LASTEXITCODE -eq 0) { Write-OK "Infrastructure restarted." }
    else { Write-Err "Restart failed." }
    Pop-Location
}

# --- Tasks ---

function Invoke-ApplyFormat {
    Write-Header "Code Formatting (Spotless)"
    $root = Get-ProjectRoot
    if (-Not (Test-Path (Join-Path $root "mvnw.cmd"))) { Write-Err "mvnw.cmd not found at: $root"; return }
    Push-Location $root
    Write-Step "Checking formatting..."
    & .\mvnw.cmd -DskipTests spotless:check 2>&1 | Where-Object { $_ -notmatch "Downloading|Downloaded" }
    if ($LASTEXITCODE -eq 0) { Write-OK "All files already formatted."; Pop-Location; return }
    Write-Step "Applying fixes..."
    & .\mvnw.cmd -DskipTests spotless:apply 2>&1 | Where-Object { $_ -notmatch "Downloading|Downloaded" }
    if ($LASTEXITCODE -eq 0) { Write-OK "Formatting applied successfully." } else { Write-Err "Formatting failed." }
    Pop-Location
}

function Invoke-SortAnnotations {
    Write-Header "Sort Entity Annotations"
    $root   = Get-ProjectRoot
    $script = Join-Path $PSScriptRoot "sort-annotations.ps1"
    if (-Not (Test-Path $script)) { Write-Err "sort-annotations.ps1 not found."; return }
    $entityDir = Join-Path $root "src\main\java\com\fptu\math_master\entity"
    & $script -EntityDir $entityDir
    Write-OK "Done."
}

function Invoke-CleanBuild {
    Write-Header "Clean Maven Build"
    $root = Get-ProjectRoot
    if (-Not (Test-Path (Join-Path $root "mvnw.cmd"))) { Write-Err "mvnw.cmd not found."; return }
    Push-Location $root
    Import-ProjectEnvFile $root | Out-Null
    Write-Step "Running: mvnw clean compile..."
    & .\mvnw.cmd clean compile -q 2>&1 | Where-Object { $_ -notmatch "Downloading|Downloaded" }
    if ($LASTEXITCODE -eq 0) { Write-OK "Build successful." } else { Write-Err "Build failed." }
    Pop-Location
}

function Invoke-CleanBuildStart {
    Write-Header "Clean Build + Start (local DB)"
    $root = Get-ProjectRoot
    if (-Not (Test-Path (Join-Path $root "mvnw.cmd"))) {
        Write-Err "mvnw.cmd not found at: $root"
        Write-Host "  Expected: ...\math-master\mvnw.cmd" -ForegroundColor Yellow
        Write-Host "  Run from repo or reinstall alias: scripts\ps_scripts\install.ps1" -ForegroundColor Yellow
        return
    }
    Write-OK "Project root: $root"

    $loadedEnv = Import-ProjectEnvFile $root
    if ($loadedEnv) { Write-OK "Env: $loadedEnv" }

    if (-not (Test-LocalPostgresReady $root)) { return }

    if (Assert-Docker) {
        Push-Location $root
        Write-Step "Freeing port 8080 (Docker app container)..."
        docker compose rm -f -s app 2>&1 | Out-Null
        Write-Step "Starting Docker infra..."
        docker compose up -d @script:InfraServices
        if ($LASTEXITCODE -ne 0) {
            Write-Err "Failed to start infrastructure. Is Docker Desktop running?"
            Pop-Location
            return
        }
        Write-OK "Redis / MinIO / Centrifugo ready."
        Pop-Location
    }

    Push-Location $root
    Write-Step "Maven clean compile..."
    & .\mvnw.cmd clean compile -q 2>&1 | Where-Object { $_ -notmatch "Downloading|Downloaded" }
    if ($LASTEXITCODE -ne 0) { Write-Err "Build failed."; Pop-Location; return }
    Write-OK "Build successful."

    if (-not (Invoke-LocalFlywayMigrate $root)) { Pop-Location; return }

    Stop-AppPort -Port 8080

    Write-Step "Starting Spring Boot (profile local, Flyway + Hibernate)..."
    Write-Host "  API: http://localhost:8080  |  Ctrl+C to stop" -ForegroundColor Gray
    Write-Host ""
    & .\mvnw.cmd spring-boot:run
    Pop-Location
}

function Invoke-StartRedis {
    Write-Header "Start Redis"
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot; Push-Location $root
    docker compose up -d redis
    if ($LASTEXITCODE -eq 0) { Write-OK "Redis started." } else { Write-Err "Failed." }
    Pop-Location
}

function Invoke-StopRedis {
    Write-Header "Stop Redis"
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot; Push-Location $root
    docker compose stop redis
    if ($LASTEXITCODE -eq 0) { Write-OK "Redis stopped." } else { Write-Err "Failed." }
    Pop-Location
}

function Invoke-RestartRedis {
    Write-Header "Restart Redis"
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot; Push-Location $root
    docker compose restart redis
    if ($LASTEXITCODE -eq 0) { Write-OK "Redis restarted." } else { Write-Err "Failed." }
    Pop-Location
}

function Invoke-StartAll {
    Write-Header "Start All Docker Services"
    Write-Host "  Local dev uses PostgreSQL on the host (pgAdmin), not Docker postgres." -ForegroundColor Yellow
    Write-Host "  Prefer menu [8] Start Infra, or [4] Clean Build + Start." -ForegroundColor Yellow
    $confirm = Read-Host "  Start ALL compose services including postgres container? (y/N)"
    if ($confirm -ne 'y') { Invoke-StartInfra; return }
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot; Push-Location $root
    docker compose up -d
    if ($LASTEXITCODE -eq 0) { Write-OK "All services started."; docker compose ps }
    else { Write-Err "Some services failed." }
    Pop-Location
}

function Invoke-StopAll {
    Write-Header "Stop All Docker Services"
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot; Push-Location $root
    docker compose down
    if ($LASTEXITCODE -eq 0) { Write-OK "All services stopped." } else { Write-Err "Failed." }
    Pop-Location
}

function Invoke-RestartAll {
    Write-Header "Restart All Docker Services"
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot; Push-Location $root
    docker compose restart
    if ($LASTEXITCODE -eq 0) { Write-OK "Restarted."; docker compose ps }
    else { Write-Err "Failed." }
    Pop-Location
}

function Invoke-DeployLocal {
    Write-Header "Docker Deploy (full stack in containers)"
    Write-Host "  For daily dev with pgAdmin DB, use menu [4] instead." -ForegroundColor Yellow
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot
    Import-ProjectEnvFile $root | Out-Null
    Push-Location $root
    docker compose build --no-cache
    if ($LASTEXITCODE -ne 0) { Write-Err "Build failed."; Pop-Location; return }
    docker compose up -d
    if ($LASTEXITCODE -eq 0) { Write-OK "Done."; docker compose ps }
    else { Write-Err "Start failed." }
    Pop-Location
}

function Invoke-DeployFull {
    Write-Header "Docker Full Deploy"
    Write-Host "  For daily dev with pgAdmin DB, use menu [4] instead." -ForegroundColor Yellow
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot
    Import-ProjectEnvFile $root | Out-Null
    Push-Location $root
    docker compose down --remove-orphans
    docker image prune -f | Out-Null
    docker compose build --no-cache
    if ($LASTEXITCODE -ne 0) { Write-Err "Build failed."; Pop-Location; return }
    docker compose up -d
    if ($LASTEXITCODE -eq 0) { Write-OK "Done."; docker compose ps }
    else { Write-Err "Start failed." }
    Pop-Location
}

function Invoke-RecreateDocker {
    Write-Header "Recreate Docker Containers"
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot; Push-Location $root
    $target = if ($Service) { $Service } else { "" }
    if ($target) {
        docker compose up -d --force-recreate $target
    } else {
        docker compose up -d --force-recreate @script:InfraServices
    }
    if ($LASTEXITCODE -eq 0) { Write-OK "Done."; docker compose ps }
    else { Write-Err "Failed." }
    Pop-Location
}

function Invoke-Logs($service, $title) {
    Write-Header $title
    if (-not (Assert-Docker)) { return }
    $root = Get-ProjectRoot; Push-Location $root
    if ($service) {
        docker compose logs -f --tail=100 $service
    } else {
        docker compose logs -f --tail=50
    }
    Pop-Location
}

function Invoke-Status {
    Write-Header "Project Status"
    $root = Get-ProjectRoot
    Import-ProjectEnvFile $root | Out-Null
    Write-Host "Database (from .env):" -ForegroundColor Cyan
    Write-Host "  $($env:SPRING_DATASOURCE_URL)" -ForegroundColor White
    Write-Host ""
    if (Assert-Docker) {
        Push-Location $root
        docker compose ps
        Pop-Location
    }
    Write-Host ""
}

function Invoke-DeleteLogs {
    Write-Header "Delete Log Files"
    $root = Get-ProjectRoot
    $deleted = 0
    foreach ($dir in @((Join-Path $root "scripts\logs"), (Join-Path $root "logs"))) {
        if (Test-Path $dir) {
            Get-ChildItem -Path $dir -Include "*.log","*.txt" -Recurse -ErrorAction SilentlyContinue |
                ForEach-Object { Remove-Item $_.FullName -Force; $deleted++ }
        }
    }
    if ($deleted -gt 0) { Write-OK "Deleted $deleted file(s)." }
    else { Write-Host "No log files found." -ForegroundColor Yellow }
}

function Invoke-SetupLocalDb {
    Write-Header "Setup Local PostgreSQL (math_learning)"
    $script = Join-Path $PSScriptRoot "setup-local-db.ps1"
    if (-Not (Test-Path $script)) { Write-Err "setup-local-db.ps1 not found."; return }
    & $script
}

function Show-Help {
    Write-Host ""
    Write-Host "Math Master Manager (local PostgreSQL)" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  First time: [5] Setup DB  ->  [4] Clean Build + Start" -ForegroundColor Green
    Write-Host ""
    Write-Host "  khoipd_terminal_ps -Task CleanBuildStart" -ForegroundColor White
    Write-Host "  Docs: math-master/docs/LOCAL_DATABASE.md" -ForegroundColor Gray
    Write-Host ""
}

function Show-MainMenu {
    Write-Host ""
    Write-Host "  Math Master Manager (local dev)" -ForegroundColor Cyan
    Write-Host ("  " + "-" * 35) -ForegroundColor DarkCyan
    Write-Host "  [4]  Clean Build + Start (+ Flyway migrate)" -ForegroundColor Green
    Write-Host "  [5]  Setup Local DB (pgAdmin / psql)" -ForegroundColor Yellow
    Write-Host ("  " + "-" * 35) -ForegroundColor DarkGray
    Write-Host "  [1]  Format Code (Spotless)" -ForegroundColor White
    Write-Host "  [2]  Sort Annotations" -ForegroundColor White
    Write-Host "  [3]  Maven Clean Build" -ForegroundColor White
    Write-Host ("  " + "-" * 35) -ForegroundColor DarkGray
    Write-Host "  [6]  Start Redis" -ForegroundColor White
    Write-Host "  [7]  Stop Redis" -ForegroundColor White
    Write-Host "  [8]  Start Infra (redis+minio+centrifugo)" -ForegroundColor White
    Write-Host "  [9]  Stop All Docker" -ForegroundColor White
    Write-Host ("  " + "-" * 35) -ForegroundColor DarkGray
    Write-Host "  [11] Docker full deploy (optional)" -ForegroundColor DarkGray
    Write-Host "  [17] Status" -ForegroundColor White
    Write-Host "  [19] Help / docs hint" -ForegroundColor White
    Write-Host "  [0]  Exit" -ForegroundColor DarkGray
    Write-Host ""
}

if ($Help -or $Task -eq 'Help') { Show-Help; exit 0 }

if ($Task) {
    switch ($Task) {
        'ApplyFormat'      { Invoke-ApplyFormat }
        'SortAnnotations'  { Invoke-SortAnnotations }
        'CleanBuild'       { Invoke-CleanBuild }
        'CleanBuildStart'  { Invoke-CleanBuildStart }
        'StartRedis'       { Invoke-StartRedis }
        'StopRedis'        { Invoke-StopRedis }
        'RestartRedis'     { Invoke-RestartRedis }
        'StartInfra'       { Invoke-StartInfra }
        'StopInfra'        { Invoke-StopInfra }
        'RestartInfra'     { Invoke-RestartInfra }
        'StartAll'         { Invoke-StartAll }
        'StopAll'          { Invoke-StopAll }
        'RestartAll'       { Invoke-RestartAll }
        'DeployLocal'      { Invoke-DeployLocal }
        'DeployFull'       { Invoke-DeployFull }
        'RecreateDocker'   { Invoke-RecreateDocker }
        'Logs'             { Invoke-Logs "" "All Logs" }
        'LogsRedis'        { Invoke-Logs "redis" "Redis Logs" }
        'LogsApp'          { Invoke-Logs "app" "App Logs" }
        'Status'           { Invoke-Status }
        'DeleteLogs'       { Invoke-DeleteLogs }
        'SetupLocalDb'     { Invoke-SetupLocalDb }
    }
    exit 0
}

while ($true) {
    Show-MainMenu
    $choice = Read-Host "  Select"
    switch ($choice) {
        '4'  { Invoke-CleanBuildStart }
        '5'  { Invoke-SetupLocalDb }
        '1'  { Invoke-ApplyFormat }
        '2'  { Invoke-SortAnnotations }
        '3'  { Invoke-CleanBuild }
        '6'  { Invoke-StartRedis }
        '7'  { Invoke-StopRedis }
        '8'  { Invoke-StartInfra }
        '9'  { Invoke-StopAll }
        '11' { Invoke-DeployLocal }
        '17' { Invoke-Status }
        '19' { Show-Help }
        '0'  { Write-Host ""; Write-Host "  Goodbye!" -ForegroundColor Cyan; exit 0 }
        default {
            if ($choice) {
                Write-Host "  Unknown option. First time: [5] then [4]." -ForegroundColor Yellow
            }
        }
    }
}
