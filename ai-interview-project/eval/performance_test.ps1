# Week 12 P1 - RAG Performance Test
# Tests hybrid search + reranking latency
# Target: p95 < 250ms

param(
    [int]$Concurrency = 50,
    [int]$TotalRequests = 100,
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "       Week 12 P1 - RAG Performance Test" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Concurrency: $Concurrency" -ForegroundColor White
Write-Host "  Total Requests: $TotalRequests" -ForegroundColor White
Write-Host "  Base URL: $BaseUrl" -ForegroundColor White
Write-Host ""

# Test queries (simulating interview questions)
$testQueries = @(
    "What is the difference between HashMap and ConcurrentHashMap in Java?",
    "Explain Spring Boot dependency injection and its benefits",
    "How does React useState hook work internally?",
    "Describe the event loop in Node.js",
    "What are microservices and when should you use them?",
    "Explain database indexing and its performance impact",
    "How does garbage collection work in Java?",
    "What is the difference between SQL and NoSQL databases?"
)

# Results storage
$results = [System.Collections.Concurrent.ConcurrentBag[object]]::new()
$errors = [System.Collections.Concurrent.ConcurrentBag[string]]::new()

Write-Host "Starting performance test..." -ForegroundColor Green
$startTime = Get-Date

# Create runspace pool for concurrent requests
$RunspacePool = [runspacefactory]::CreateRunspacePool(1, $Concurrency)
$RunspacePool.Open()
$Jobs = @()

for ($i = 0; $i -lt $TotalRequests; $i++) {
    $PowerShell = [powershell]::Create()
    $PowerShell.RunspacePool = $RunspacePool
    
    [void]$PowerShell.AddScript({
        param($BaseUrl, $Query, $Index, $Results, $Errors)
        
        try {
            $requestStart = Get-Date
            
            # Test RAG hybrid search + reranking endpoint (P1-AC3)
            $encodedQuery = [Uri]::EscapeDataString($Query)
            $response = Invoke-WebRequest `
                -Uri "$BaseUrl/api/test/rag/search?query=$encodedQuery&topK=5" `
                -Method GET `
                -UseBasicParsing `
                -TimeoutSec 10 `
                -ErrorAction Stop
            
            $requestEnd = Get-Date
            $latencyMs = ($requestEnd - $requestStart).TotalMilliseconds
            
            $result = @{
                Index = $Index
                Latency = $latencyMs
                Status = $response.StatusCode
                Success = ($response.StatusCode -eq 200 -or $response.StatusCode -eq 401)
            }
            
            $Results.Add($result)
            
        } catch {
            $Errors.Add("Request $Index failed: $_")
        }
    })
    
    [void]$PowerShell.AddArgument($BaseUrl)
    [void]$PowerShell.AddArgument($testQueries[$i % $testQueries.Count])
    [void]$PowerShell.AddArgument($i)
    [void]$PowerShell.AddArgument($results)
    [void]$PowerShell.AddArgument($errors)
    
    $Jobs += @{
        PowerShell = $PowerShell
        Handle = $PowerShell.BeginInvoke()
    }
}

# Wait for all jobs to complete
Write-Host "Running $TotalRequests requests with $Concurrency concurrent workers..." -ForegroundColor Yellow
$completed = 0
while ($Jobs | Where-Object { -not $_.Handle.IsCompleted }) {
    $newCompleted = ($Jobs | Where-Object { $_.Handle.IsCompleted }).Count
    if ($newCompleted -gt $completed) {
        $completed = $newCompleted
        $pct = [math]::Round(($completed / $TotalRequests) * 100)
        Write-Host "Progress: $completed / $TotalRequests ( $pct percent )" -ForegroundColor Green
    }
    Start-Sleep -Milliseconds 200
}
Write-Host "Progress: $TotalRequests / $TotalRequests ( 100 percent )" -ForegroundColor Green

# Cleanup
$Jobs | ForEach-Object {
    $_.PowerShell.EndInvoke($_.Handle)
    $_.PowerShell.Dispose()
}
$RunspacePool.Close()
$RunspacePool.Dispose()

$endTime = Get-Date
$totalDuration = ($endTime - $startTime).TotalSeconds

# Analyze results
Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "                        RESULTS" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

$successfulResults = $results | Where-Object { $_.Success }
$latencies = $successfulResults | ForEach-Object { $_.Latency } | Sort-Object

if ($latencies.Count -eq 0) {
    Write-Host "[FAIL] No successful requests!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Errors:" -ForegroundColor Red
    $errors | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkRed }
    exit 1
}

# Calculate percentiles
function Get-Percentile {
    param([double[]]$Values, [double]$Percentile)
    $index = [math]::Ceiling($Values.Count * $Percentile / 100) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $Values.Count) { $index = $Values.Count - 1 }
    return $Values[$index]
}

$p50 = Get-Percentile $latencies 50
$p95 = Get-Percentile $latencies 95
$p99 = Get-Percentile $latencies 99
$min = $latencies[0]
$max = $latencies[-1]
$avg = ($latencies | Measure-Object -Average).Average

Write-Host "Request Statistics:" -ForegroundColor Yellow
Write-Host ("  Total Requests:    {0,6}" -f $TotalRequests) -ForegroundColor White
Write-Host ("  Successful:        {0,6}" -f $successfulResults.Count) -ForegroundColor Green
Write-Host ("  Failed:            {0,6}" -f ($TotalRequests - $successfulResults.Count)) -ForegroundColor $(if ($errors.Count -gt 0) { "Red" } else { "White" })
$successRate = $successfulResults.Count / $TotalRequests
Write-Host ("  Success Rate:      {0,6:P1}" -f $successRate) -ForegroundColor White
Write-Host ("  Total Duration:    {0,6:F2}s" -f $totalDuration) -ForegroundColor White
$rps = $TotalRequests / $totalDuration
Write-Host ("  Requests/sec:      {0,6:F2}" -f $rps) -ForegroundColor White

Write-Host ""
Write-Host "Latency Distribution:" -ForegroundColor Yellow
Write-Host ("  Min:               {0,6:F2} ms" -f $min) -ForegroundColor White
Write-Host ("  p50 (Median):      {0,6:F2} ms" -f $p50) -ForegroundColor White
Write-Host ("  Average:           {0,6:F2} ms" -f $avg) -ForegroundColor White
$p95Color = if ($p95 -lt 250) { "Green" } else { "Red" }
Write-Host ("  p95:               {0,6:F2} ms" -f $p95) -ForegroundColor $p95Color
Write-Host ("  p99:               {0,6:F2} ms" -f $p99) -ForegroundColor White
Write-Host ("  Max:               {0,6:F2} ms" -f $max) -ForegroundColor White

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "                   ACCEPTANCE CRITERIA" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "P1-AC3: p95 latency less than 250ms" -ForegroundColor Yellow
if ($p95 -lt 250) {
    $roundedP95 = [math]::Round($p95, 2)
    Write-Host "  [PASS] p95 = $roundedP95 ms (less than 250ms)" -ForegroundColor Green
} else {
    $roundedP95 = [math]::Round($p95, 2)
    Write-Host "  [FAIL] p95 = $roundedP95 ms (greater than or equal to 250ms)" -ForegroundColor Red
}

if ($errors.Count -gt 0) {
    Write-Host ""
    Write-Host "Errors encountered:" -ForegroundColor Red
    $errors | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkRed }
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

# Return exit code based on AC
if ($p95 -lt 250) {
    exit 0
} else {
    exit 1
}
