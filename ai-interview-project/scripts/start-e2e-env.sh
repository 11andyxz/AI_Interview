#!/bin/bash
# E2E Test Environment Setup Script
# This script starts all required services for E2E testing

set -e  # Exit on error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
MYSQL_PORT=3306
BACKEND_PORT=8080
FRONTEND_PORT=3000
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

# Log functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if port is in use
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        return 0  # Port is in use
    else
        return 1  # Port is free
    fi
}

# Wait for service to be ready
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1

    log_info "Waiting for $service_name to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            log_info "$service_name is ready!"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    log_error "$service_name failed to start within $((max_attempts * 2)) seconds"
    return 1
}

# Kill process on port
kill_port() {
    local port=$1
    if check_port $port; then
        log_warn "Killing existing process on port $port"
        kill $(lsof -t -i:$port) 2>/dev/null || true
        sleep 2
    fi
}

# Step 1: Check Prerequisites
log_info "Step 1/8: Checking prerequisites..."

if ! command -v mysql &> /dev/null; then
    log_error "MySQL is not installed or not in PATH"
    exit 1
fi

if ! command -v java &> /dev/null; then
    log_error "Java is not installed or not in PATH"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    log_error "Maven is not installed or not in PATH"
    exit 1
fi

if ! command -v node &> /dev/null; then
    log_error "Node.js is not installed or not in PATH"
    exit 1
fi

if ! command -v npm &> /dev/null; then
    log_error "npm is not installed or not in PATH"
    exit 1
fi

log_info "All prerequisites are installed ✓"

# Step 2: Start MySQL Test Database
log_info "Step 2/8: Starting MySQL test database..."

# Check if MySQL is running
if ! mysqladmin ping -h localhost --silent 2>/dev/null; then
    log_error "MySQL server is not running. Please start MySQL first."
    exit 1
fi

# Create test database if not exists
mysql -u root -p"${MYSQL_ROOT_PASSWORD:-root}" -e "CREATE DATABASE IF NOT EXISTS ai_interview_test;" 2>/dev/null || {
    log_warn "Failed to create database (may already exist)"
}

log_info "MySQL test database is ready ✓"

# Step 3: Seed Test Data
log_info "Step 3/8: Seeding test data..."

cd "$BACKEND_DIR"

# Run database migrations
mvn flyway:migrate -Dflyway.url=jdbc:mysql://localhost:3306/ai_interview_test -Dflyway.user=root -Dflyway.password="${MYSQL_ROOT_PASSWORD:-root}" || {
    log_warn "Database migration failed or already up-to-date"
}

# Seed test data using SQL script
mysql -u root -p"${MYSQL_ROOT_PASSWORD:-root}" ai_interview_test < "$PROJECT_ROOT/docs/mysql_test_data.sql" 2>/dev/null || {
    log_warn "Test data seeding failed (may already exist)"
}

log_info "Test data seeded ✓"

# Step 4: Start Backend (Test Profile)
log_info "Step 4/8: Starting backend on port $BACKEND_PORT..."

# Kill existing backend if running
kill_port $BACKEND_PORT

# Build backend
cd "$BACKEND_DIR"
log_info "Building backend..."
mvn clean package -DskipTests -q || {
    log_error "Backend build failed"
    exit 1
}

# Start backend in background with test profile
log_info "Starting backend server..."
SPRING_PROFILES_ACTIVE=test java -jar target/backend-0.0.1-SNAPSHOT.jar > /tmp/backend-e2e.log 2>&1 &
BACKEND_PID=$!
echo $BACKEND_PID > /tmp/backend-e2e.pid

# Wait for backend to be ready
if ! wait_for_service "http://localhost:$BACKEND_PORT/actuator/health" "Backend"; then
    log_error "Backend failed to start. Check logs at /tmp/backend-e2e.log"
    cat /tmp/backend-e2e.log
    exit 1
fi

log_info "Backend is ready ✓ (PID: $BACKEND_PID)"

# Step 5: Start Frontend (Development Server)
log_info "Step 5/8: Starting frontend on port $FRONTEND_PORT..."

# Kill existing frontend if running
kill_port $FRONTEND_PORT

cd "$FRONTEND_DIR"

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    log_info "Installing frontend dependencies..."
    npm install
fi

# Start frontend in background
log_info "Starting frontend dev server..."
REACT_APP_API_URL=http://localhost:$BACKEND_PORT npm start > /tmp/frontend-e2e.log 2>&1 &
FRONTEND_PID=$!
echo $FRONTEND_PID > /tmp/frontend-e2e.pid

# Wait for frontend to be ready
if ! wait_for_service "http://localhost:$FRONTEND_PORT" "Frontend"; then
    log_error "Frontend failed to start. Check logs at /tmp/frontend-e2e.log"
    cat /tmp/frontend-e2e.log
    exit 1
fi

log_info "Frontend is ready ✓ (PID: $FRONTEND_PID)"

# Step 6: Run Health Checks
log_info "Step 6/8: Running health checks..."

# Backend health check
backend_health=$(curl -s http://localhost:$BACKEND_PORT/actuator/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
if [ "$backend_health" != "UP" ]; then
    log_error "Backend health check failed: $backend_health"
    exit 1
fi
log_info "Backend health: UP ✓"

# Frontend health check
frontend_status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$FRONTEND_PORT)
if [ "$frontend_status" != "200" ]; then
    log_error "Frontend health check failed: HTTP $frontend_status"
    exit 1
fi
log_info "Frontend health: UP ✓"

# Database health check
if ! mysql -u root -p"${MYSQL_ROOT_PASSWORD:-root}" -e "SELECT 1" ai_interview_test &>/dev/null; then
    log_error "Database health check failed"
    exit 1
fi
log_info "Database health: UP ✓"

# Step 7: Execute Playwright Tests
log_info "Step 7/8: Executing Playwright tests..."

cd "$FRONTEND_DIR"

# Install Playwright if not already installed
if [ ! -d "node_modules/@playwright/test" ]; then
    log_info "Installing Playwright..."
    npm install --save-dev @playwright/test
fi

# Install Playwright browsers if needed
if [ ! -d "$HOME/.cache/ms-playwright" ]; then
    log_info "Installing Playwright browsers..."
    npx playwright install
fi

# Run Playwright tests
log_info "Running E2E tests..."
npx playwright test --config=playwright.config.js --reporter=html,json,junit || {
    log_warn "Some tests failed. Check report for details."
}

# Step 8: Generate HTML Report
log_info "Step 8/8: Generating HTML test report..."

# Open Playwright HTML report
if [ -d "playwright-report" ]; then
    log_info "Opening test report in browser..."
    npx playwright show-report &
    REPORT_PID=$!
    echo $REPORT_PID > /tmp/playwright-report.pid
fi

# Save test results
if [ -f "test-results.json" ]; then
    cp test-results.json "$PROJECT_ROOT/frontend/e2e/results/e2e-test-results-$(date +%Y%m%d-%H%M%S).json"
    log_info "Test results saved ✓"
fi

log_info "================================"
log_info "E2E Environment is ready!"
log_info "================================"
log_info "Backend:  http://localhost:$BACKEND_PORT"
log_info "Frontend: http://localhost:$FRONTEND_PORT"
log_info "MySQL:    localhost:$MYSQL_PORT (database: ai_interview_test)"
log_info ""
log_info "Process IDs:"
log_info "  Backend:  $BACKEND_PID"
log_info "  Frontend: $FRONTEND_PID"
log_info ""
log_info "Logs:"
log_info "  Backend:  /tmp/backend-e2e.log"
log_info "  Frontend: /tmp/frontend-e2e.log"
log_info ""
log_info "To stop all services, run:"
log_info "  kill $BACKEND_PID $FRONTEND_PID"
log_info "  or use: ./scripts/stop-e2e-env.sh"
log_info "================================"

# Keep script running (optional - comment out if you want it to exit)
# trap "log_info 'Stopping services...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit 0" INT TERM
# wait
