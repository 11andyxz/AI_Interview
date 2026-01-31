"""
Load Testing Script for AI Interview Platform
Implements various load testing scenarios with comprehensive monitoring
"""

import asyncio
import aiohttp
import time
import json
import yaml
import psutil
import argparse
import sys
from datetime import datetime
from typing import Dict, List, Tuple
from collections import defaultdict
import statistics
import matplotlib.pyplot as plt
import mysql.connector
from pathlib import Path

class LoadTestMetrics:
    """Collects and aggregates load test metrics"""
    
    def __init__(self):
        self.requests = []
        self.errors = []
        self.start_time = None
        self.end_time = None
        self.active_users = 0
        self.system_metrics = []
        
    def record_request(self, endpoint: str, latency: float, status_code: int, success: bool):
        """Record a single request"""
        self.requests.append({
            'timestamp': time.time(),
            'endpoint': endpoint,
            'latency': latency,
            'status_code': status_code,
            'success': success
        })
        
        if not success:
            self.errors.append({
                'timestamp': time.time(),
                'endpoint': endpoint,
                'status_code': status_code
            })
    
    def record_system_metrics(self, cpu: float, memory: float, db_connections: int):
        """Record system resource metrics"""
        self.system_metrics.append({
            'timestamp': time.time(),
            'cpu': cpu,
            'memory': memory,
            'db_connections': db_connections
        })
    
    def get_percentile(self, latencies: List[float], percentile: int) -> float:
        """Calculate latency percentile"""
        if not latencies:
            return 0.0
        sorted_latencies = sorted(latencies)
        index = int(len(sorted_latencies) * (percentile / 100))
        return sorted_latencies[min(index, len(sorted_latencies) - 1)]
    
    def get_summary(self) -> Dict:
        """Generate summary statistics"""
        if not self.requests:
            return {}
        
        latencies = [r['latency'] for r in self.requests]
        successful = [r for r in self.requests if r['success']]
        
        duration = (self.end_time - self.start_time) if self.end_time else 0
        throughput = len(self.requests) / duration if duration > 0 else 0
        
        return {
            'total_requests': len(self.requests),
            'successful_requests': len(successful),
            'failed_requests': len(self.errors),
            'error_rate': len(self.errors) / len(self.requests) if self.requests else 0,
            'success_rate': len(successful) / len(self.requests) if self.requests else 0,
            'avg_latency': statistics.mean(latencies) if latencies else 0,
            'median_latency': statistics.median(latencies) if latencies else 0,
            'p50_latency': self.get_percentile(latencies, 50),
            'p75_latency': self.get_percentile(latencies, 75),
            'p90_latency': self.get_percentile(latencies, 90),
            'p95_latency': self.get_percentile(latencies, 95),
            'p99_latency': self.get_percentile(latencies, 99),
            'max_latency': max(latencies) if latencies else 0,
            'min_latency': min(latencies) if latencies else 0,
            'throughput': throughput,
            'duration': duration,
            'avg_cpu': statistics.mean([m['cpu'] for m in self.system_metrics]) if self.system_metrics else 0,
            'avg_memory': statistics.mean([m['memory'] for m in self.system_metrics]) if self.system_metrics else 0,
            'max_cpu': max([m['cpu'] for m in self.system_metrics]) if self.system_metrics else 0,
            'max_memory': max([m['memory'] for m in self.system_metrics]) if self.system_metrics else 0
        }

class LoadTestRunner:
    """Executes load tests based on scenario configuration"""
    
    def __init__(self, base_url: str, scenario_config: Dict):
        self.base_url = base_url
        self.config = scenario_config
        self.metrics = LoadTestMetrics()
        self.auth_tokens = {}
        self.interview_ids = []
        self.db_config = {
            'host': 'localhost',
            'user': 'root',
            'password': 'root',
            'database': 'ai_interview_test'
        }
        
    async def authenticate(self, session: aiohttp.ClientSession, user_id: int) -> str:
        """Authenticate user and return JWT token"""
        if user_id in self.auth_tokens:
            return self.auth_tokens[user_id]
        
        credentials = {
            'username': f'test-user-{user_id}@example.com',
            'password': 'testpass123'
        }
        
        try:
            async with session.post(f'{self.base_url}/api/auth/login', json=credentials) as response:
                if response.status == 200:
                    data = await response.json()
                    token = data.get('token')
                    self.auth_tokens[user_id] = token
                    return token
        except Exception as e:
            print(f'Authentication failed for user {user_id}: {e}')
        
        return None
    
    async def make_request(self, session: aiohttp.ClientSession, endpoint_config: Dict, user_id: int):
        """Make a single HTTP request"""
        path = endpoint_config['path']
        method = endpoint_config['method']
        requires_auth = endpoint_config.get('requires_auth', False)
        
        # Replace placeholders
        if '{user_id}' in path:
            path = path.replace('{user_id}', str(user_id))
        if '{interview_id}' in path and self.interview_ids:
            interview_id = self.interview_ids[user_id % len(self.interview_ids)]
            path = path.replace('{interview_id}', interview_id)
        
        url = f'{self.base_url}{path}'
        headers = {}
        
        if requires_auth:
            token = await self.authenticate(session, user_id)
            if token:
                headers['Authorization'] = f'Bearer {token}'
        
        body = endpoint_config.get('body')
        if body and isinstance(body, dict):
            # Replace placeholders in body
            body = json.loads(json.dumps(body).replace('{user_id}', str(user_id)))
        
        start_time = time.time()
        success = False
        status_code = 0
        
        try:
            if method == 'GET':
                async with session.get(url, headers=headers, timeout=aiohttp.ClientTimeout(total=30)) as response:
                    status_code = response.status
                    success = 200 <= status_code < 300
                    await response.text()  # Consume response
            elif method == 'POST':
                async with session.post(url, headers=headers, json=body, timeout=aiohttp.ClientTimeout(total=30)) as response:
                    status_code = response.status
                    success = 200 <= status_code < 300
                    data = await response.json()
                    # Store interview ID if created
                    if 'id' in data and 'interview' in path.lower():
                        self.interview_ids.append(data['id'])
            elif method == 'PUT':
                async with session.put(url, headers=headers, json=body, timeout=aiohttp.ClientTimeout(total=30)) as response:
                    status_code = response.status
                    success = 200 <= status_code < 300
            elif method == 'DELETE':
                async with session.delete(url, headers=headers, timeout=aiohttp.ClientTimeout(total=30)) as response:
                    status_code = response.status
                    success = 200 <= status_code < 300
        except asyncio.TimeoutError:
            status_code = 408
        except Exception as e:
            print(f'Request error: {e}')
            status_code = 500
        
        latency = (time.time() - start_time) * 1000  # Convert to ms
        self.metrics.record_request(path, latency, status_code, success)
    
    def get_db_connection_count(self) -> int:
        """Get current database connection count"""
        try:
            conn = mysql.connector.connect(**self.db_config)
            cursor = conn.cursor()
            cursor.execute("SHOW STATUS LIKE 'Threads_connected'")
            result = cursor.fetchone()
            conn.close()
            return int(result[1]) if result else 0
        except:
            return 0
    
    async def monitor_system(self):
        """Monitor system resources"""
        while True:
            cpu = psutil.cpu_percent(interval=1)
            memory = psutil.virtual_memory().percent
            db_connections = self.get_db_connection_count()
            
            self.metrics.record_system_metrics(cpu, memory, db_connections)
            await asyncio.sleep(5)
    
    async def simulate_user(self, user_id: int, duration: int, think_time: Tuple[float, float]):
        """Simulate single user behavior"""
        async with aiohttp.ClientSession() as session:
            start_time = time.time()
            
            while (time.time() - start_time) < duration:
                # Select endpoint based on weight
                endpoint = self.select_endpoint_by_weight()
                await self.make_request(session, endpoint, user_id)
                
                # Think time between requests
                await asyncio.sleep(asyncio.uniform(think_time[0], think_time[1]))
    
    def select_endpoint_by_weight(self) -> Dict:
        """Select endpoint based on configured weight"""
        import random
        endpoints = self.config.get('endpoints', [])
        weights = [e.get('weight', 1) for e in endpoints]
        return random.choices(endpoints, weights=weights)[0]
    
    async def run_normal_scenario(self):
        """Run normal load scenario"""
        print(f'Running scenario: {self.config["name"]}')
        print(f'Users: {self.config["users"]}, Duration: {self.config["duration"]}s')
        
        self.metrics.start_time = time.time()
        
        # Start system monitoring
        monitor_task = asyncio.create_task(self.monitor_system())
        
        # Ramp up users
        ramp_up = self.config.get('ramp_up', 0)
        duration = self.config['duration']
        num_users = self.config['users']
        think_time = (self.config['think_time']['min'], self.config['think_time']['max'])
        
        tasks = []
        for i in range(num_users):
            if ramp_up > 0:
                await asyncio.sleep(ramp_up / num_users)
            task = asyncio.create_task(self.simulate_user(i, duration, think_time))
            tasks.append(task)
            self.metrics.active_users += 1
        
        # Wait for all users to complete
        await asyncio.gather(*tasks)
        
        self.metrics.end_time = time.time()
        self.metrics.active_users = 0
        
        # Stop monitoring
        monitor_task.cancel()
        
        return self.metrics.get_summary()
    
    async def run_stress_scenario(self):
        """Run stress test scenario"""
        print(f'Running scenario: {self.config["name"]}')
        
        self.metrics.start_time = time.time()
        monitor_task = asyncio.create_task(self.monitor_system())
        
        users_start = self.config['users_start']
        users_max = self.config['users_max']
        users_step = self.config['users_step']
        step_duration = self.config['step_duration']
        think_time = (self.config['think_time']['min'], self.config['think_time']['max'])
        
        breaking_criteria = self.config.get('breaking_criteria', {})
        
        current_users = users_start
        all_tasks = []
        
        while current_users <= users_max:
            print(f'Ramping up to {current_users} users...')
            
            # Start new user tasks
            new_tasks = []
            for i in range(current_users - len(all_tasks)):
                user_id = len(all_tasks) + i
                task = asyncio.create_task(self.simulate_user(user_id, step_duration, think_time))
                new_tasks.append(task)
            
            all_tasks.extend(new_tasks)
            self.metrics.active_users = current_users
            
            # Wait for step duration
            await asyncio.sleep(step_duration)
            
            # Check breaking criteria
            summary = self.metrics.get_summary()
            if summary.get('error_rate', 0) > breaking_criteria.get('error_rate', 1.0):
                print(f'Breaking: Error rate {summary["error_rate"]:.2%} exceeded threshold')
                break
            if summary.get('p95_latency', 0) > breaking_criteria.get('p95_latency', float('inf')):
                print(f'Breaking: P95 latency {summary["p95_latency"]:.0f}ms exceeded threshold')
                break
            
            current_users += users_step
        
        # Wait for remaining tasks
        await asyncio.gather(*all_tasks, return_exceptions=True)
        
        self.metrics.end_time = time.time()
        self.metrics.active_users = 0
        monitor_task.cancel()
        
        return self.metrics.get_summary()
    
    async def run_spike_scenario(self):
        """Run spike test scenario"""
        print(f'Running scenario: {self.config["name"]}')
        
        self.metrics.start_time = time.time()
        monitor_task = asyncio.create_task(self.monitor_system())
        
        phases = self.config.get('phases', [])
        think_time = (self.config['think_time']['min'], self.config['think_time']['max'])
        
        for phase in phases:
            print(f'Phase: {phase["name"]} ({phase["users"]} users, {phase["duration"]}s)')
            
            tasks = []
            for i in range(phase['users']):
                task = asyncio.create_task(self.simulate_user(i, phase['duration'], think_time))
                tasks.append(task)
            
            self.metrics.active_users = phase['users']
            await asyncio.gather(*tasks)
        
        self.metrics.end_time = time.time()
        self.metrics.active_users = 0
        monitor_task.cancel()
        
        return self.metrics.get_summary()
    
    async def run(self) -> Dict:
        """Run the configured scenario"""
        scenario_type = self.config.get('name', '').lower()
        
        if 'stress' in scenario_type:
            return await self.run_stress_scenario()
        elif 'spike' in scenario_type:
            return await self.run_spike_scenario()
        else:
            return await self.run_normal_scenario()

def generate_report(scenario_name: str, summary: Dict, output_dir: str):
    """Generate load test report"""
    output_path = Path(output_dir) / f'load_test_report_{scenario_name}_{datetime.now().strftime("%Y%m%d_%H%M%S")}.md'
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'w') as f:
        f.write(f'# Load Test Report: {scenario_name}\n\n')
        f.write(f'**Generated:** {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}\n\n')
        
        f.write('## Summary\n\n')
        f.write(f'- **Total Requests:** {summary["total_requests"]}\n')
        f.write(f'- **Successful:** {summary["successful_requests"]} ({summary["success_rate"]:.2%})\n')
        f.write(f'- **Failed:** {summary["failed_requests"]} ({summary["error_rate"]:.2%})\n')
        f.write(f'- **Duration:** {summary["duration"]:.2f}s\n')
        f.write(f'- **Throughput:** {summary["throughput"]:.2f} req/s\n\n')
        
        f.write('## Latency Metrics\n\n')
        f.write(f'- **Average:** {summary["avg_latency"]:.2f}ms\n')
        f.write(f'- **Median (P50):** {summary["p50_latency"]:.2f}ms\n')
        f.write(f'- **P75:** {summary["p75_latency"]:.2f}ms\n')
        f.write(f'- **P90:** {summary["p90_latency"]:.2f}ms\n')
        f.write(f'- **P95:** {summary["p95_latency"]:.2f}ms\n')
        f.write(f'- **P99:** {summary["p99_latency"]:.2f}ms\n')
        f.write(f'- **Max:** {summary["max_latency"]:.2f}ms\n')
        f.write(f'- **Min:** {summary["min_latency"]:.2f}ms\n\n')
        
        f.write('## Resource Utilization\n\n')
        f.write(f'- **Average CPU:** {summary["avg_cpu"]:.2f}%\n')
        f.write(f'- **Max CPU:** {summary["max_cpu"]:.2f}%\n')
        f.write(f'- **Average Memory:** {summary["avg_memory"]:.2f}%\n')
        f.write(f'- **Max Memory:** {summary["max_memory"]:.2f}%\n\n')
    
    print(f'Report generated: {output_path}')
    return str(output_path)

async def main():
    """Main entry point"""
    parser = argparse.ArgumentParser(description='Run load tests for AI Interview Platform')
    parser.add_argument('--scenario', type=str, default='normal', choices=['normal', 'peak', 'stress', 'spike'],
                        help='Load test scenario to run')
    parser.add_argument('--base-url', type=str, default='http://localhost:8080',
                        help='Base URL of the API')
    parser.add_argument('--config', type=str, default='eval/load_test_scenarios.yml',
                        help='Path to scenarios configuration file')
    parser.add_argument('--output', type=str, default='eval/results',
                        help='Output directory for reports')
    
    args = parser.parse_args()
    
    # Load configuration
    with open(args.config, 'r') as f:
        config = yaml.safe_load(f)
    
    scenario_config = config.get(args.scenario)
    if not scenario_config:
        print(f'Scenario "{args.scenario}" not found in configuration')
        sys.exit(1)
    
    # Run load test
    runner = LoadTestRunner(args.base_url, scenario_config)
    summary = await runner.run()
    
    # Generate report
    report_path = generate_report(args.scenario, summary, args.output)
    
    # Print summary
    print('\n=== Load Test Summary ===')
    print(f'Scenario: {args.scenario}')
    print(f'Total Requests: {summary["total_requests"]}')
    print(f'Success Rate: {summary["success_rate"]:.2%}')
    print(f'Error Rate: {summary["error_rate"]:.2%}')
    print(f'P95 Latency: {summary["p95_latency"]:.2f}ms')
    print(f'P99 Latency: {summary["p99_latency"]:.2f}ms')
    print(f'Throughput: {summary["throughput"]:.2f} req/s')
    print(f'\nReport: {report_path}')

if __name__ == '__main__':
    asyncio.run(main())
