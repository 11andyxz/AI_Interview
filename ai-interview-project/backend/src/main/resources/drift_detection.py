#!/usr/bin/env python3
"""
Simple ML drift detection using KL divergence
Compares current week metrics vs 30-day baseline
"""
import mysql.connector
import numpy as np
from datetime import datetime, timedelta
from scipy.stats import entropy

# Database configuration
DB_CONFIG = {
    'host': '104.197.94.13',
    'user': 'root',
    'password': 'ai-interview-db-pwd',
    'database': 'ai_interview'
}

def calculate_kl_divergence(p, q, epsilon=1e-10):
    """Calculate KL divergence between two distributions"""
    p = np.array(p) + epsilon
    q = np.array(q) + epsilon
    p = p / p.sum()
    q = q / q.sum()
    return entropy(p, q)

def fetch_metrics(days_back):
    """Fetch metrics from database"""
    conn = mysql.connector.connect(**DB_CONFIG)
    cursor = conn.cursor()
    
    start_date = datetime.now() - timedelta(days=days_back)
    query = """
        SELECT validation_pass_rate, avg_latency_ms, error_rate 
        FROM ai_metrics_log 
        WHERE created_at >= %s
    """
    cursor.execute(query, (start_date,))
    results = cursor.fetchall()
    
    cursor.close()
    conn.close()
    return results

def detect_drift():
    """Main drift detection logic"""
    # Get current week (7 days) and baseline (30 days)
    current_week = fetch_metrics(7)
    baseline = fetch_metrics(30)
    
    if len(current_week) < 10 or len(baseline) < 10:
        print("Insufficient data for drift detection")
        return
    
    # Extract feature distributions
    current_dist = [float(r[0]) for r in current_week if r[0] is not None]
    baseline_dist = [float(r[0]) for r in baseline if r[0] is not None]
    
    if len(current_dist) == 0 or len(baseline_dist) == 0:
        print("No valid data for comparison")
        return
    
    # Calculate KL divergence
    kl_div = calculate_kl_divergence(current_dist, baseline_dist)
    
    print(f"KL Divergence: {kl_div:.4f}")
    
    # Alert threshold
    if kl_div > 0.1:
        print(f"DRIFT ALERT: KL divergence {kl_div:.4f} exceeds threshold 0.1")
    else:
        print("No significant drift detected")

if __name__ == "__main__":
    detect_drift()
