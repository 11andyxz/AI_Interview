#!/usr/bin/env python3
"""
ML Drift Detection using KL Divergence, Chi-square, and KS tests
Compares current week metrics vs 30-day baseline
"""
import mysql.connector
import numpy as np
from datetime import datetime, timedelta
from scipy.stats import entropy, chisquare, ks_2samp

# Database configuration
DB_CONFIG = {
    'host': '104.197.94.13',
    'user': 'andy',
    'password': '123456',
    'database': 'ai_interview'
}

def calculate_kl_divergence(p, q, epsilon=1e-10):
    """Calculate KL divergence between two distributions"""
    p = np.array(p) + epsilon
    q = np.array(q) + epsilon
    p = p / p.sum()
    q = q / q.sum()
    return entropy(p, q)

def fetch_metric_values(metric_name, days_back):
    """Fetch specific metric values from database"""
    conn = mysql.connector.connect(**DB_CONFIG)
    cursor = conn.cursor()
    
    start_date = datetime.now() - timedelta(days=days_back)
    query = """
        SELECT metric_value, timestamp
        FROM ai_metrics_log 
        WHERE metric_name = %s AND timestamp >= %s
        ORDER BY timestamp DESC
    """
    cursor.execute(query, (metric_name, start_date))
    results = cursor.fetchall()
    
    cursor.close()
    conn.close()
    return [float(r[0]) for r in results if r[0] is not None]

def detect_drift():
    """Main drift detection logic with multiple statistical tests"""
    print("=" * 60)
    print("ML Drift Detection Report")
    print(f"Generated at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)
    
    # Metrics to monitor for drift
    metrics_to_check = [
        'ai_validation_pass_rate',
        'ai_request_latency_ms',
        'ai_quality_score',
        'ai_cost_per_request'
    ]
    
    drift_detected = False
    
    for metric_name in metrics_to_check:
        print(f"\n--- {metric_name} ---")
        
        # Get current week (7 days) and baseline (30 days)
        current_week = fetch_metric_values(metric_name, 7)
        baseline = fetch_metric_values(metric_name, 30)
        
        if len(current_week) < 5 or len(baseline) < 10:
            print(f"⚠️  Insufficient data: current={len(current_week)}, baseline={len(baseline)}")
            continue
        
        # 1. KL Divergence (for distributions)
        try:
            # Create histograms for KL divergence
            bins = np.histogram_bin_edges(baseline + current_week, bins=10)
            current_hist, _ = np.histogram(current_week, bins=bins)
            baseline_hist, _ = np.histogram(baseline, bins=bins)
            
            kl_div = calculate_kl_divergence(current_hist, baseline_hist)
            print(f"KL Divergence: {kl_div:.4f}")
            
            if kl_div > 0.1:
                print(f"🚨 DRIFT ALERT (KL): {kl_div:.4f} > 0.1")
                drift_detected = True
        except Exception as e:
            print(f"KL divergence calculation failed: {e}")
        
        # 2. Kolmogorov-Smirnov test (for continuous distributions)
        try:
            ks_stat, ks_pvalue = ks_2samp(current_week, baseline)
            print(f"KS Test: statistic={ks_stat:.4f}, p-value={ks_pvalue:.4f}")
            
            if ks_pvalue < 0.05:
                print(f"🚨 DRIFT ALERT (KS): p-value {ks_pvalue:.4f} < 0.05")
                drift_detected = True
        except Exception as e:
            print(f"KS test failed: {e}")
        
        # 3. Basic statistics comparison
        current_mean = np.mean(current_week)
        baseline_mean = np.mean(baseline)
        percent_change = ((current_mean - baseline_mean) / baseline_mean) * 100 if baseline_mean != 0 else 0
        
        print(f"Mean: current={current_mean:.4f}, baseline={baseline_mean:.4f}, change={percent_change:+.2f}%")
        
        if abs(percent_change) > 30:
            print(f"🚨 DRIFT ALERT (Mean): {abs(percent_change):.2f}% > 30%")
            drift_detected = True
    
    print("\n" + "=" * 60)
    if drift_detected:
        print("⚠️  OVERALL STATUS: DRIFT DETECTED")
    else:
        print("✅ OVERALL STATUS: NO SIGNIFICANT DRIFT")
    print("=" * 60)

if __name__ == "__main__":
    detect_drift()
