#!/usr/bin/env python3
"""
Return the most recent completed eval_results_*.csv file, respecting a run lock if present.
Usage:
  python get_latest_result.py --dir results/ [--wait-seconds 0]
If a lock file exists, this will return the most recent timestamped result file older than the lock creation time.
If --wait-seconds > 0 and lock exists, waits up to that many seconds for the lock to disappear, then returns the newest file.
"""
import argparse
import os
import time
import glob
from datetime import datetime


def find_latest_completed(results_dir, lock_name='eval_running.lock'):
    lock_path = os.path.join(results_dir, lock_name)
    lock_ctime = None
    if os.path.exists(lock_path):
        lock_ctime = os.path.getctime(lock_path)

    pattern = os.path.join(results_dir, 'eval_results_*.csv')
    files = glob.glob(pattern)
    if not files:
        return None

    # sort by mtime descending
    files.sort(key=lambda p: os.path.getmtime(p), reverse=True)

    if lock_ctime:
        # pick first file with mtime <= lock_ctime (completed before run started)
        for f in files:
            if os.path.getmtime(f) <= lock_ctime:
                return f
        # no file older than lock -> return None
        return None
    else:
        return files[0]


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--dir', default='results', help='results directory')
    p.add_argument('--wait-seconds', type=int, default=0, help='Wait up to N seconds if a run is in progress')
    args = p.parse_args()

    results_dir = args.dir
    lock_path = os.path.join(results_dir, 'eval_running.lock')

    waited = 0
    poll_interval = 1
    while args.wait_seconds and os.path.exists(lock_path) and waited < args.wait_seconds:
        time.sleep(poll_interval)
        waited += poll_interval

    if os.path.exists(lock_path):
        # try to pick latest completed file older than lock
        latest = find_latest_completed(results_dir)
        if latest:
            print(latest)
            raise SystemExit(0)
        else:
            print('RUNNING')
            raise SystemExit(2)
    else:
        latest = find_latest_completed(results_dir)
        if latest:
            print(latest)
            raise SystemExit(0)
        else:
            print('NO_RESULTS')
            raise SystemExit(1)
