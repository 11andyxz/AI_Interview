# E2E Test Environment Setup Script (PowerShell)
# This script starts all required services for E2E testing on Windows

param(
    [string]$MySQLPassword = "root"
)

# Configuration
$MYSQL_PORT = 3306
$BACKEND_PORT = 8080
$FRONTEND_PORT = 3000
$PROJECT_ROOT = Split-Path -Parent $PSScriptRoot
$BACKEND_DIR = Join-Path $PROJECT_ROOT "backend"
$FRONTEND_DIR = Join-Path $PROJECT_ROOT "frontend"

# Color output functions
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Check if port is in use
function Test-Port {
    param([int]$Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    return $null -ne $connection
}

# Wait for service to be ready
function Wait-ForService {
    param(
        [string]$Url,
        [string]$ServiceName,
        [int]$MaxAttempts = 30
    )
    
    Write-Info "Waiting for $ServiceName to be ready..."
    
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $Url -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 404) {
                Write-Info "$ServiceName is ready!"
                return $true
            }
        }
        catch {
            # Service not ready yet
        }
        
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 2
    }
    
    Write-Error-Custom "$ServiceName failed to start within $($MaxAttempts * 2) seconds"
    return $false
}

# Kill process on port
function Stop-ProcessOnPort {
    param([int]$Port)
    
    $process = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty OwningProcess -Unique
    
    if ($process) {
        Write-Warn "Killing existing process on port $Port"
        Stop-Process -Id $process -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
}

# Step 1: Check Prerequisites
Write-Info "Step 1/8: Checking prerequisites..."

$prerequisites = @(
    @{Name="MySQL"; Command="mysql"},
    @{Name="Java"; Command="java"},
    @{Name="Maven"; Command="mvn"},
    @{Name="Node.js"; Command="node"},
    @{Name="npm"; Command="npm"}
)

foreach ($prereq in $prerequisites) {
    if (-not (Get-Command $prereq.Command -ErrorAction SilentlyContinue)) {
        Write-Error-Custom "$($prereq.Name) is not installed or not in PATH"
        exit 1
    }
}

Write-Info "All prerequisites are installed ✓"

# Step 2: Start MySQL Test Database
Write-Info "Step 2/8: Setting up MySQL test database..."

# Test MySQL connection
try {
    $mysqlTest = mysql -u root -p"$MySQLPassword" -e "SELECT 1" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error-Custom "Cannot connect to MySQL. Please check MySQL is running and password is correct."
        exit 1
    }
}
catch {
    Write-Error-Custom "MySQL server is not running. Please start MySQL first."
    exit 1
}

# Create test database
mysql -u root -p"$MySQLPassword" -e "CREATE DATABASE IF NOT EXISTS ai_interview_test;" 2>$null
Write-Info "MySQL test database is ready ✓"

# Step 3: Seed Test Data
Write-Info "Step 3/8: Seeding test data..."

Push-Location $BACKEND_DIR

# Run database migrations
Write-Info "Running database migrations..."
mvn flyway:migrate "-Dflyway.url=jdbc:mysql://localhost:3306/ai_interview_test" "-Dflyway.user=root" "-Dflyway.password=$MySQLPassword" -q 2>$null

# Seed test data
$testDataFile = Join-Path $PROJECT_ROOT "docs\mysql_test_data.sql"
if (Test-Path $testDataFile) {
    mysql -u root -p"$MySQLPassword" ai_interview_test < $testDataFile 2>$null
}

Write-Info "Test data seeded ✓"

Pop-Location

# Step 4: Start Backend (Test Profile)
Write-Info "Step 4/8: Starting backend on port $BACKEND_PORT..."

# Kill existing backend
Stop-ProcessOnPort $BACKEND_PORT

# Build backend
Push-Location $BACKEND_DIR
Write-Info "Building backend..."
mvn clean package -DskipTests -q

if ($LASTEXITCODE -ne 0) {
    Write-Error-Custom "Backend build failed"
    Pop-Location
    exit 1
}

# Start backend in background
Write-Info "Starting backend server..."
$env:SPRING_PROFILES_ACTIVE = "test"
$backendLog = Join-Path $env:TEMP "backend-e2e.log"
$backendProcess = Start-Process -FilePath "java" -ArgumentList "-jar", "target\backend-0.0.1-SNAPSHOT.jar" -PassThru -RedirectStandardOutput $backendLog -RedirectStandardError $backendLog -WindowStyle Hidden
$backendProcess.Id | Out-File -FilePath (Join-Path $env:TEMP "backend-e2e.pid")

Pop-Location

# Wait for backend
if (-not (Wait-ForService "http://localhost:$BACKEND_PORT/actuator/health" "Backend")) {
    Write-Error-Custom "Backend failed to start. Check logs at $backendLog"
    Get-Content $backendLog -Tail 50
    exit 1
}

Write-Info "Backend is ready ✓ (PID: $($backendProcess.Id))"

# Step 5: Start Frontend (Development Server)
Write-Info "Step 5/8: Starting frontend on port $FRONTEND_PORT..."

# Kill existing frontend
Stop-ProcessOnPort $FRONTEND_PORT

Push-Location $FRONTEND_DIR

# Install dependencies if needed
if (-not (Test-Path "node_modules")) {
    Write-Info "Installing frontend dependencies..."
    npm install
}

# Start frontend in background
Write-Info "Starting frontend dev server..."
$env:REACT_APP_API_URL = "http://localhost:$BACKEND_PORT"
$frontendLog = Join-Path $env:TEMP "frontend-e2e.log"
$frontendProcess = Start-Process -FilePath "npm" -ArgumentList "start" -PassThru -RedirectStandardOutput $frontendLog -RedirectStandardError $frontendLog -WindowStyle Hidden
$frontendProcess.Id | Out-File -FilePath (Join-Path $env:TEMP "frontend-e2e.pid")

Pop-Location

# Wait for frontend
if (-not (Wait-ForService "http://localhost:$FRONTEND_PORT" "Frontend")) {
    Write-Error-Custom "Frontend failed to start. Check logs at $frontendLog"
    Get-Content $frontendLog -Tail 50
    exit 1
}

Write-Info "Frontend is ready ✓ (PID: $($frontendProcess.Id))"

# Step 6: Run Health Checks
Write-Info "Step 6/8: Running health checks..."

# Backend health
try {
    $healthResponse = Invoke-RestMethod -Uri "http://localhost:$BACKEND_PORT/actuator/health" -TimeoutSec 5
    if ($healthResponse.status -ne "UP") {
        Write-Error-Custom "Backend health check failed: $($healthResponse.status)"
        exit 1
    }
    Write-Info "Backend health: UP ✓"
}
catch {
    Write-Error-Custom "Backend health check failed: $_"
    exit 1
}

# Frontend health
try {
    $frontendResponse = Invoke-WebRequest -Uri "http://localhost:$FRONTEND_PORT" -TimeoutSec 5 -UseBasicParsing
    if ($frontendResponse.StatusCode -ne 200) {
        Write-Error-Custom "Frontend health check failed: HTTP $($frontendResponse.StatusCode)"
        exit 1
    }
    Write-Info "Frontend health: UP ✓"
}
catch {
    Write-Error-Custom "Frontend health check failed: $_"
    exit 1
}

# Database health
try {
    mysql -u root -p"$MySQLPassword" -e "SELECT 1" ai_interview_test 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Database query failed"
    }
    Write-Info "Database health: UP ✓"
}
catch {
    Write-Error-Custom "Database health check failed"
    exit 1
}

# Step 7: Execute Playwright Tests
Write-Info "Step 7/8: Executing Playwright tests..."

Push-Location $FRONTEND_DIR

# Install Playwright if needed
if (-not (Test-Path "node_modules\@playwright")) {
    Write-Info "Installing Playwright..."
    npm install --save-dev @playwright/test
}

# Install browsers if needed
$playwrightCache = Join-Path $env:USERPROFILE ".cache\ms-playwright"
if (-not (Test-Path $playwrightCache)) {
    Write-Info "Installing Playwright browsers..."
    npx playwright install
}

# Run tests
Write-Info "Running E2E tests..."
npx playwright test --config=playwright.config.js --reporter=html,json,junit

if ($LASTEXITCODE -ne 0) {
    Write-Warn "Some tests failed. Check report for details."
}

Pop-Location

# Step 8: Generate HTML Report
Write-Info "Step 8/8: Generating HTML test report..."

Push-Location $FRONTEND_DIR

if (Test-Path "playwright-report") {
    Write-Info "Opening test report in browser..."
    Start-Process "npx" -ArgumentList "playwright", "show-report" -WindowStyle Hidden
}

# Save test results
if (Test-Path "test-results.json") {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $resultsDir = Join-Path $FRONTEND_DIR "e2e\results"
    if (-not (Test-Path $resultsDir)) {
        New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null
    }
    Copy-Item "test-results.json" -Destination (Join-Path $resultsDir "e2e-test-results-$timestamp.json")
    Write-Info "Test results saved ✓"
}

Pop-Location

# Summary
Write-Host ""
Write-Info "================================"
Write-Info "E2E Environment is ready!"
Write-Info "================================"
Write-Info "Backend:  http://localhost:$BACKEND_PORT"
Write-Info "Frontend: http://localhost:$FRONTEND_PORT"
Write-Info "MySQL:    localhost:$MYSQL_PORT (database: ai_interview_test)"
Write-Host ""
Write-Info "Process IDs:"
Write-Info "  Backend:  $($backendProcess.Id)"
Write-Info "  Frontend: $($frontendProcess.Id)"
Write-Host ""
Write-Info "Logs:"
Write-Info "  Backend:  $backendLog"
Write-Info "  Frontend: $frontendLog"
Write-Host ""
Write-Info "To stop all services, run:"
Write-Info "  Stop-Process -Id $($backendProcess.Id),$($frontendProcess.Id)"
Write-Info "  or use: .\scripts\stop-e2e-env.ps1"
Write-Info "================================"
Write-Host ""
Write-Host "Press Ctrl+C to stop all services..." -ForegroundColor Cyan

# Keep processes running
try {
    while ($true) {
        Start-Sleep -Seconds 10
        # Check if processes are still running
        if (-not (Get-Process -Id $backendProcess.Id -ErrorAction SilentlyContinue)) {
            Write-Warn "Backend process has stopped"
            break
        }
        if (-not (Get-Process -Id $frontendProcess.Id -ErrorAction SilentlyContinue)) {
            Write-Warn "Frontend process has stopped"
            break
        }
    }
}
finally {
    Write-Info "Stopping services..."
    Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $frontendProcess.Id -Force -ErrorAction SilentlyContinue
}
