#!/usr/bin/env python3
"""Extract baseline experiment data from database"""
import mysql.connector
import json
import os
from datetime import datetime

def extract_baseline_data():
    conn = mysql.connector.connect(
        host=os.getenv('DB_HOST', 'mysql-4c9be66-andyxiongzheng-9267.g.aivencloud.com'),
        port=int(os.getenv('DB_PORT', '22629')),
        user=os.getenv('DB_USERNAME', 'avnadmin'),
        password=os.getenv('DB_PASSWORD'),
        database=os.getenv('DB_NAME', 'ai_interview')
    )
    
    cursor = conn.cursor(dictionary=True)
    
    # Get recent baseline sessions (created in last hour)
    cursor.execute('''
        SELECT 
            i.id,
            i.position_type,
            i.status,
            i.created_at,
            COUNT(im.id) as question_count
        FROM interview i
        LEFT JOIN interview_message im ON i.id = im.interview_id AND im.role = 'assistant'
        WHERE i.created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
        GROUP BY i.id
        ORDER BY i.created_at DESC
    ''')
    
    sessions = cursor.fetchall()
    
    print(f"✓ 提取到 {len(sessions)} 个baseline会话\n")
    
    total_questions = 0
    for idx, s in enumerate(sessions, 1):
        print(f"  #{idx}: {s['position_type']:30s} | {s['question_count']} 问题 | {s['status']}")
        total_questions += s['question_count']
    
    if sessions:
        avg_questions = total_questions / len(sessions) 
        print(f"\n=== Baseline Metrics ===")
        print(f"Total Sessions: {len(sessions)}")
        print(f"Avg Questions: {avg_questions:.1f}")
        print(f"Total Questions: {total_questions}")
    
    cursor.close()
    conn.close()
    
    return sessions

if __name__ == "__main__":
    extract_baseline_data()
