#!/usr/bin/env python3
"""
Export training data from API to versioned JSONL format
"""

import requests
import argparse
from datetime import datetime, timedelta
from pathlib import Path
import json


def export_training_data(api_base: str, from_date: str, to_date: str, output_dir: Path):
    """Export training data via API"""
    
    print(f"Exporting training data from {from_date} to {to_date}...")
    
    # Call export API
    response = requests.post(
        f"{api_base}/api/ml/training/export",
        params={
            "from": from_date,
            "to": to_date
        }
    )
    
    if response.status_code == 200:
        result = response.json()
        print(f"✓ Export successful!")
        print(f"  Export path: {result['export_path']}")
        print(f"  Message: {result['message']}")
        return result['export_path']
    else:
        print(f"✗ Export failed: {response.status_code}")
        print(response.text)
        return None


def export_preference_pairs(api_base: str):
    """Export preference pairs for RLHF/DPO"""
    
    print("Exporting preference pairs...")
    
    response = requests.post(f"{api_base}/api/ml/training/export/preferences")
    
    if response.status_code == 200:
        result = response.json()
        print(f"✓ Export successful!")
        print(f"  Export path: {result['export_path']}")
        return result['export_path']
    else:
        print(f"✗ Export failed: {response.status_code}")
        print(response.text)
        return None


def fetch_feedback_stats(api_base: str, days: int = 7):
    """Get feedback statistics"""
    
    print(f"\nFetching feedback stats (last {days} days)...")
    
    response = requests.get(
        f"{api_base}/api/ml/training/feedback/stats",
        params={"days": days}
    )
    
    if response.status_code == 200:
        stats = response.json()
        print("\nFeedback Statistics:")
        print(f"  👍 Thumbs up: {stats['thumbs_up']}")
        print(f"  👎 Thumbs down: {stats['thumbs_down']}")
        print(f"  🚩 Flags: {stats['flags']}")
        print(f"  ✏️  Corrections: {stats['corrections']}")
        print(f"  📊 Total: {stats['total']}")
        return stats
    else:
        print(f"✗ Failed to fetch stats: {response.status_code}")
        return None


def main():
    parser = argparse.ArgumentParser(description="Export training data from API")
    parser.add_argument("--api-base", type=str, default="http://localhost:8080",
                       help="API base URL")
    parser.add_argument("--from-date", type=str,
                       help="Start date (YYYY-MM-DD)")
    parser.add_argument("--to-date", type=str,
                       help="End date (YYYY-MM-DD)")
    parser.add_argument("--days", type=int, default=30,
                       help="Number of days to export (if dates not specified)")
    parser.add_argument("--preferences", action="store_true",
                       help="Export preference pairs instead")
    parser.add_argument("--feedback-stats", action="store_true",
                       help="Show feedback statistics")
    parser.add_argument("--output", type=str,
                       help="Output directory (optional)")
    
    args = parser.parse_args()
    
    # Calculate date range if not provided
    if not args.from_date or not args.to_date:
        to_date = datetime.now()
        from_date = to_date - timedelta(days=args.days)
        args.from_date = from_date.strftime("%Y-%m-%d")
        args.to_date = to_date.strftime("%Y-%m-%d")
    
    output_dir = Path(args.output) if args.output else Path("eval/training_data/exports")
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Export data
    if args.preferences:
        export_path = export_preference_pairs(args.api_base)
    else:
        export_path = export_training_data(args.api_base, args.from_date, args.to_date, output_dir)
    
    # Show feedback stats
    if args.feedback_stats:
        fetch_feedback_stats(args.api_base, days=7)
    
    print("\n✅ Export complete!")


if __name__ == "__main__":
    main()
