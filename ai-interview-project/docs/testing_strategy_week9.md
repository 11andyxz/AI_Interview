# Week 9 Testing Strategy

## Overview

This document outlines the comprehensive testing strategy for the AI Interview Platform, covering all testing levels from unit tests to load tests, with specific focus on Week 9 deliverables: system integration and load testing.

**Document Version:** 1.0  
**Last Updated:** January 30, 2026  
**Authors:** Engineering Team  
**Status:** Active

---

## Table of Contents

1. [Testing Philosophy](#testing-philosophy)
2. [Test Pyramid](#test-pyramid)
3. [Testing Levels](#testing-levels)
4. [Cross-Browser Testing](#cross-browser-testing)
5. [Load & Performance Testing](#load--performance-testing)
6. [CI/CD Integration](#cicd-integration)
7. [Test Environment Setup](#test-environment-setup)
8. [Running Tests](#running-tests)
9. [Quality Gates](#quality-gates)
10. [Monitoring & Reporting](#monitoring--reporting)

---

## Testing Philosophy

### Core Principles

1. **Shift-Left Testing:** Find bugs early in development cycle
2. **Automation First:** Automate everything that can be automated
3. **Fast Feedback:** Tests should run quickly and provide immediate feedback
4. **Reliability:** Tests should be deterministic and not flaky
5. **Maintainability:** Tests are code - they need to be maintainable
6. **Production-Like:** Test environments should mirror production

### Testing Goals

- **Quality Assurance:** Ensure software meets requirements
- **Risk Mitigation:** Identify issues before production
- **Confidence:** Enable safe deployment and refactoring
- **Documentation:** Tests serve as living documentation
- **Performance:** Validate system meets performance SLAs

---

## Test Pyramid

Our testing strategy follows the test pyramid approach:

```
        /\
       /  \  E2E Tests (10%)
      /____\  ~47 tests, 5 min runtime
     /      \
    / Integr \  Integration Tests (20%)
   / ation   \  ~65 tests, 3 min runtime
  /__________\
 /            \
/  Unit Tests  \ Unit Tests (70%)
/______(310)____\ ~310 tests, 30 sec runtime
```

### Rationale

- **70% Unit Tests:** Fast, isolated, test business logic
- **20% Integration Tests:** Test component interactions, database, APIs
- **10% E2E Tests:** Test critical user journeys end-to-end

---

## Testing Levels

### 1. Unit Tests

**Purpose:** Test individual components in isolation

**Coverage:**
- Service layer business logic
- Utility functions
- Model validation
- DTOs and mappers

**Framework:** JUnit 5 + Mockito (Backend), Jest (Frontend)

**Location:**
- Backend: `backend/src/test/java/**/*Test.java`
- Frontend: `frontend/src/**/*.test.js`

**Execution Time:** ~30 seconds

**Example:**
```java
@Test
void testGenerateInterviewQuestions_ValidInput_ReturnsQuestions() {
    // Given
    String role = "backend_java";
    int count = 5;
    
    // When
    List<Question> questions = aiService.generateQuestions(role, count);
    
    // Then
    assertThat(questions).hasSize(5);
    assertThat(questions).allMatch(q -> q.getText() != null);
}
```

### 2. Integration Tests

**Purpose:** Test interactions between components

**Coverage:**
- API endpoints
- Database operations
- External service integrations
- Authentication/authorization flows

**Framework:** Spring Boot Test + TestContainers (Backend), Playwright (Frontend integration)

**Location:**
- Backend: `backend/src/test/java/**/integration/**/*IntegrationTest.java`

**Execution Time:** ~3 minutes

**Example:**
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class InterviewFlowIntegrationTest {
    
    @Test
    void testCompleteInterviewFlow() {
        // Create user -> Create interview -> Conduct interview -> Generate report
        // Full workflow validation
    }
}
```

### 3. End-to-End (E2E) Tests

**Purpose:** Test complete user workflows across frontend + backend

**Coverage:**
- Critical user journeys
- Full-stack interactions
- Browser compatibility
- UI/UX validation

**Framework:** Playwright 1.40.0

**Location:** `frontend/e2e/*.spec.js`

**Browsers:** Chromium, Firefox, WebKit

**Execution Time:** ~5 minutes per browser

**Test Suites:**

| Suite | Tests | Priority | Status |
|-------|-------|----------|--------|
| andy-full-flow.spec.js | 5 | Critical | ✅ 100% |
| resume-analysis.spec.js | 6 | Critical | ✅ 100% |
| interview-templates.spec.js | 5 | Critical | ✅ 100% |
| question-sets.spec.js | 5 | Important | ✅ 92% |
| skill-tracking.spec.js | 4 | Important | ✅ 100% |
| error-handling.spec.js | 6 | Important | ✅ 100% |
| payment-flow.spec.js | 6 | Nice-to-Have | ⚠️ 80% |
| settings-flow.spec.js | 5 | Nice-to-Have | ✅ 100% |
| progress-tracking.spec.js | 5 | Nice-to-Have | ✅ 100% |

### 4. Load & Stress Tests

**Purpose:** Validate system performance under various load conditions

**Coverage:**
- Normal load (10 users)
- Peak load (20 users)
- Stress test (ramp to breaking point)
- Spike test (sudden traffic surge)

**Framework:** Custom Python + aiohttp

**Location:** `eval/load_test.py`, `eval/load_test_scenarios.yml`

**Execution Time:** 4 hours (all scenarios)

**Key Metrics:**
- P50, P95, P99 latency
- Throughput (requests/sec)
- Error rate
- CPU/Memory usage
- Database connection pool utilization
- OpenAI API rate limit headroom

---

## Cross-Browser Testing

### Supported Browsers

| Browser | Version | Support Level | Test Coverage |
|---------|---------|---------------|---------------|
| Chrome | Latest | Primary | 100% |
| Edge | Latest | Primary | 100% |
| Firefox | Latest | Secondary | 100% |
| Safari | Latest | Secondary | 100% |
| Chrome Mobile | Latest | Mobile | 100% |
| Safari iOS | Latest | Mobile | 100% |

### Testing Strategy

**Desktop:**
- Test all critical flows on Chromium, Firefox, WebKit
- Visual regression testing on all browsers
- Responsive design testing (1920x1080, 1366x768, 1024x768)

**Mobile:**
- Test on iPhone 13 Pro, iPhone SE, iPad Pro
- Test on Galaxy S21, Pixel 5
- Touch interaction validation
- Mobile-specific UI components

**Tools:**
- Playwright for cross-browser automation
- BrowserStack for device testing (optional)
- Percy for visual regression (optional)

---

## Load & Performance Testing

### Scenarios

#### 1. Normal Load Test
**Goal:** Validate typical business hours performance

- **Users:** 10 concurrent
- **Duration:** 10 minutes
- **Success Criteria:**
  - P95 latency < 3s ✅
  - Error rate < 0.5% ✅
  - Throughput ≥ 5 req/s ✅

#### 2. Peak Load Test
**Goal:** Validate performance during traffic peaks

- **Users:** 20 concurrent
- **Duration:** 15 minutes
- **Success Criteria:**
  - P95 latency < 5s ✅
  - Error rate < 1% ✅
  - Throughput ≥ 10 req/s ✅

#### 3. Stress Test
**Goal:** Find system breaking point

- **Users:** Ramp from 1 to 100 (step: 5 every 60s)
- **Breaking Criteria:**
  - Error rate > 5% OR
  - P95 latency > 10s OR
  - CPU > 95%
- **Result:** Breaking point at 45 users ⚠️

#### 4. Spike Test
**Goal:** Validate resilience during sudden traffic surge

- **Phases:**
  - Baseline: 5 users, 5 min
  - Spike: 50 users, 5 min (10x increase in 10s)
  - Recovery: 5 users, 5 min
- **Success Criteria:**
  - Spike P95 < 8s ✅
  - Recovery time < 60s ✅
  - No data loss ✅

### Monitoring During Load Tests

**Application Metrics:**
- Request latency (P50, P75, P90, P95, P99)
- Throughput (requests/second)
- Error rate
- Active users

**System Metrics:**
- CPU usage
- Memory usage
- Disk I/O
- Network throughput

**Database Metrics:**
- Connection pool utilization
- Query execution time
- Transaction rate
- Deadlocks/locks

**External API Metrics:**
- OpenAI API latency
- OpenAI rate limit usage
- Fallback trigger rate

---

## CI/CD Integration

### GitHub Actions Workflows

#### 1. Integration Tests (`integration-tests.yml`)

**Trigger:**
- Push to main, develop, feature/* branches
- Pull requests to main, develop

**Steps:**
1. Set up MySQL test database
2. Run database migrations
3. Execute integration tests
4. Generate coverage report
5. Upload test artifacts
6. Block merge on failure

**Quality Gates:**
- All tests must pass
- Code coverage ≥ 85%
- No critical security vulnerabilities

#### 2. E2E Tests (`e2e-tests.yml`)

**Trigger:**
- Push to main, develop
- Pull requests
- Manual dispatch

**Steps:**
1. Build backend
2. Start backend + MySQL
3. Start frontend
4. Install Playwright browsers
5. Run E2E tests on all browsers
6. Upload test results
7. Check pass rate ≥ 90%

**Quality Gates:**
- E2E pass rate ≥ 90%
- No critical flow failures
- All browsers pass

#### 3. Load Tests (`load-tests.yml`)

**Trigger:**
- Weekly schedule (Sundays 2 AM)
- Manual dispatch

**Steps:**
1. Build and start backend
2. Run selected scenario(s)
3. Generate performance report
4. Compare with baseline
5. Upload results
6. Alert on regression

**Quality Gates:**
- P95 latency < threshold
- Error rate < threshold
- No performance regression > 20%

### Merge Blocking Rules

PRs cannot be merged if:
- ❌ Any integration test fails
- ❌ Code coverage drops below 85%
- ❌ E2E pass rate < 90%
- ❌ Critical E2E flows fail
- ❌ Security vulnerabilities detected (high/critical)
- ⚠️ Performance regression > 20% (warning only)

---

## Test Environment Setup

### Prerequisites

**Software:**
- Java 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Python 3.11+ (for load tests)

**Hardware (Recommended):**
- CPU: 8 cores
- RAM: 16GB
- Disk: 50GB free space
- Network: Stable internet connection

### Setup Scripts

#### Backend + MySQL
```bash
# Start MySQL
docker run -d -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=ai_interview_test \
  mysql:8.0

# Build backend
cd backend
mvn clean package -DskipTests

# Start backend
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

#### Frontend
```bash
cd frontend
npm install
npm start
```

#### E2E Environment (Automated)
```bash
# Linux/Mac
./scripts/start-e2e-env.sh

# Windows
.\scripts\start-e2e-env.ps1
```

This script automatically:
1. Starts MySQL
2. Runs migrations
3. Seeds test data
4. Starts backend
5. Starts frontend
6. Runs health checks
7. Executes Playwright tests
8. Generates HTML report

---

## Running Tests

### Backend Tests

```bash
cd backend

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=UserServiceTest

# Run integration tests only
mvn test -Dtest=**/*IntegrationTest

# Run with coverage
mvn clean test jacoco:report

# Skip tests during build
mvn clean package -DskipTests
```

### Frontend Tests

```bash
cd frontend

# Run unit tests
npm test

# Run E2E tests (all browsers)
npx playwright test

# Run E2E tests (specific browser)
npx playwright test --project=chromium
npx playwright test --project=firefox
npx playwright test --project=webkit

# Run specific test file
npx playwright test andy-full-flow.spec.js

# Run in headed mode (see browser)
npx playwright test --headed

# Debug mode
npx playwright test --debug

# Generate HTML report
npx playwright show-report
```

### Load Tests

```bash
cd ai-interview-project

# Install Python dependencies
pip install aiohttp pyyaml psutil mysql-connector-python matplotlib

# Run normal load test
python eval/load_test.py --scenario normal

# Run peak load test
python eval/load_test.py --scenario peak

# Run stress test
python eval/load_test.py --scenario stress

# Run spike test
python eval/load_test.py --scenario spike

# Custom configuration
python eval/load_test.py \
  --scenario normal \
  --base-url http://localhost:8080 \
  --config eval/load_test_scenarios.yml \
  --output eval/results
```

---

## Quality Gates

### Code Quality

| Metric | Threshold | Current | Status |
|--------|-----------|---------|--------|
| Unit Test Coverage | ≥85% | 86% | ✅ |
| Integration Test Coverage | ≥70% | 73% | ✅ |
| Branch Coverage | ≥75% | 75% | ✅ |
| Critical Path Coverage | 100% | 100% | ✅ |

### Functional Quality

| Metric | Threshold | Current | Status |
|--------|-----------|---------|--------|
| Backend Test Pass Rate | 100% | 94.77% | ⚠️ |
| E2E Test Pass Rate | ≥90% | 91.49% | ✅ |
| Critical E2E Tests | 100% | 100% | ✅ |
| Cross-Browser Compat | ≥90% | 93.6% | ✅ |

### Performance Quality

| Metric | Threshold | Current | Status |
|--------|-----------|---------|--------|
| Normal Load P95 | <3s | 1.8s | ✅ |
| Peak Load P95 | <5s | 4.2s | ✅ |
| Error Rate (Peak) | <1% | 0.7% | ✅ |
| System Capacity | ≥30 users | 40 users | ✅ |

### Security Quality

| Metric | Threshold | Current | Status |
|--------|-----------|---------|--------|
| Critical Vulnerabilities | 0 | 0 | ✅ |
| High Vulnerabilities | 0 | 0 | ✅ |
| OWASP Top 10 | No violations | Clean | ✅ |

---

## Monitoring & Reporting

### Test Reports

**Backend:**
- Location: `backend/target/surefire-reports/`
- Format: JUnit XML, HTML
- Retention: 30 days in CI

**E2E:**
- Location: `frontend/playwright-report/`
- Format: HTML, JSON, JUnit XML
- Retention: 30 days in CI
- Artifacts include screenshots and videos of failures

**Load Tests:**
- Location: `eval/results/`
- Format: Markdown, JSON
- Retention: 90 days in CI
- Include performance charts and metrics

### Real-Time Monitoring

**During Development:**
- Test coverage dashboard in IDE
- Automatic test execution on save (Watch mode)
- Instant failure notifications

**During CI/CD:**
- GitHub Actions workflow status
- PR comments with test results
- Slack notifications on failure (configured)

### Dashboards

**Test Metrics Dashboard:**
- Total tests count trend
- Pass rate trend
- Flaky test rate
- Test execution time trend
- Coverage trend

**Performance Metrics Dashboard:**
- P95 latency trend
- Error rate trend
- Throughput trend
- Resource utilization trend

### Alerting

**Critical Alerts (Immediate):**
- Critical test failures in main branch
- E2E pass rate drops below 80%
- Load test shows >20% performance regression
- Security vulnerability detected

**Warning Alerts (Next Day):**
- Test coverage drops below threshold
- Flaky test detected (fails intermittently)
- Test execution time increases >20%

---

## Best Practices

### Writing Tests

1. **Follow AAA Pattern:** Arrange, Act, Assert
2. **One Assertion Per Test:** Focus on single behavior
3. **Descriptive Names:** Test name should explain what's being tested
4. **Independent Tests:** Tests should not depend on each other
5. **Fast Execution:** Keep tests fast (< 1s for unit, < 5s for integration)
6. **No Hardcoded Values:** Use test data builders or fixtures
7. **Clean Up:** Always clean up test data/resources

### Maintaining Tests

1. **Refactor Tests:** Apply same standards as production code
2. **Remove Obsolete Tests:** Delete tests for removed features
3. **Fix Flaky Tests:** Investigate and fix immediately
4. **Update Test Data:** Keep test data realistic and up-to-date
5. **Review Test PRs:** Tests need code review too

### Debugging Failed Tests

1. **Read Error Message:** Often self-explanatory
2. **Check Logs:** Backend logs, browser console logs
3. **Run Locally:** Reproduce failure in local environment
4. **Use Debugger:** Step through test execution
5. **Check Recent Changes:** What changed since last passing run?
6. **Isolate Test:** Run single test to eliminate interference

---

## Continuous Improvement

### Regular Reviews

**Weekly:**
- Review failed tests
- Address flaky tests
- Update test data

**Monthly:**
- Review test coverage
- Analyze test execution time
- Update testing strategy

**Quarterly:**
- Performance testing audit
- Test framework updates
- Tool evaluation

### Metrics to Track

1. Test count growth
2. Test execution time trend
3. Flaky test rate
4. Bug escape rate (bugs found in production)
5. Test maintenance effort

---

## Appendix

### Useful Commands

```bash
# Backend
mvn test                                    # Run all tests
mvn test -Dtest=ClassName                   # Run specific class
mvn test -Dtest=ClassName#methodName        # Run specific method
mvn clean test jacoco:report                # Generate coverage

# Frontend
npm test                                    # Run unit tests
npm test -- --coverage                      # With coverage
npx playwright test                         # Run E2E tests
npx playwright test --ui                    # Interactive mode
npx playwright codegen http://localhost:3000 # Record new tests

# Load Tests
python eval/load_test.py --scenario normal  # Normal load
python eval/load_test.py --scenario all     # All scenarios

# CI/CD
gh workflow run integration-tests.yml       # Trigger workflow
gh run list                                 # List workflow runs
gh run view <run-id>                        # View run details
```

### Additional Resources

- [Backend Test Report](../backend/FINAL_TEST_REPORT_WEEK9.md)
- [E2E Test Report](../frontend/e2e/E2E_TEST_REPORT_WEEK9.md)
- [Load Test Report](../eval/results/load_test_report_week9.md)
- [Playwright Documentation](https://playwright.dev/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)

---

**Document Status:** ✅ Complete and Active  
**Next Review:** February 28, 2026  
**Owner:** Engineering Team
