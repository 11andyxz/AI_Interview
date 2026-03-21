#!/bin/bash
# Quick validation script for Week 16 Task 4 monitoring implementation

echo "=== Week 16 Task 4: Monitoring Implementation Validation ==="
echo ""

# 1. Check files exist
echo "1. Checking implementation files..."
if [ -f "backend/src/main/java/com/aiinterview/ml/monitoring/MLMetricsService.java" ]; then
    echo "   ✅ MLMetricsService.java exists"
else
    echo "   ❌ MLMetricsService.java missing"
fi

if [ -f "backend/src/main/java/com/aiinterview/ml/monitoring/MLHealthIndicator.java" ]; then
    echo "   ✅ MLHealthIndicator.java exists"
else
    echo "   ❌ MLHealthIndicator.java missing"
fi

if [ -f "backend/src/test/java/com/aiinterview/ml/monitoring/MLMetricsServiceTest.java" ]; then
    echo "   ✅ MLMetricsServiceTest.java exists"
else
    echo "   ❌ MLMetricsServiceTest.java missing"
fi

echo ""
echo "2. Checking dependencies in pom.xml..."
if grep -q "spring-boot-starter-actuator" backend/pom.xml; then
    echo "   ✅ actuator dependency added"
else
    echo "   ❌ actuator dependency missing"
fi

if grep -q "micrometer-registry-prometheus" backend/pom.xml; then
    echo "   ✅ micrometer dependency added"
else
    echo "   ❌ micrometer dependency missing"
fi

echo ""
echo "3. Checking actuator configuration..."
if grep -q "management.endpoint.prometheus.enabled" backend/src/main/resources/application.properties; then
    echo "   ✅ Prometheus endpoint configured"
else
    echo "   ❌ Prometheus endpoint not configured"
fi

echo ""
echo "4. Running unit tests..."
cd backend
mvn test -Dtest=MLMetricsServiceTest -q
TEST_RESULT=$?
cd ..

if [ $TEST_RESULT -eq 0 ]; then
    echo "   ✅ All tests passed"
else
    echo "   ❌ Tests failed"
fi

echo ""
echo "5. Code statistics..."
echo "   MLMetricsService.java: $(wc -l < backend/src/main/java/com/aiinterview/ml/monitoring/MLMetricsService.java) lines"
echo "   MLHealthIndicator.java: $(wc -l < backend/src/main/java/com/aiinterview/ml/monitoring/MLHealthIndicator.java) lines"
echo "   MLMetricsServiceTest.java: $(wc -l < backend/src/test/java/com/aiinterview/ml/monitoring/MLMetricsServiceTest.java) lines"

echo ""
echo "=== Validation Complete ===" 
