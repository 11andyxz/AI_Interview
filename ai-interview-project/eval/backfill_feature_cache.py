#!/usr/bin/env python3
"""
Backfill script: question_embedding and response_feature_cache
Uses real data from template_questions and interview_message tables.
Idempotent: skips rows that already exist.
"""

import os
import json
import struct
import mysql.connector
from datetime import datetime, timezone
from openai import OpenAI

DB_CONFIG = {
    "host": os.environ.get("DB_HOST", "mysql-4c9be66-andyxiongzheng-9267.g.aivencloud.com"),
    "port": int(os.environ.get("DB_PORT", "22629")),
    "database": os.environ.get("DB_NAME", "ai_interview"),
    "user": os.environ.get("DB_USERNAME", "avnadmin"),
    "password": os.environ.get("DB_PASSWORD", ""),
    "connection_timeout": 10,
    "ssl_disabled": False,
}

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
EMBEDDING_MODEL = "text-embedding-3-small"  # 1536 dims, cheaper than ada-002


def get_embedding(client: OpenAI, text: str) -> list[float]:
    resp = client.embeddings.create(model=EMBEDDING_MODEL, input=text)
    return resp.data[0].embedding


def floats_to_blob(floats: list[float]) -> bytes:
    """Pack float list to binary blob (IEEE 754 little-endian)."""
    return struct.pack(f"<{len(floats)}f", *floats)


def backfill_question_embeddings(conn, client: OpenAI) -> dict:
    cursor = conn.cursor()
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")

    # template_questions with their template role_id mapping
    cursor.execute("""
        SELECT tq.id, tq.question_text, tq.template_id
        FROM template_questions tq
        WHERE tq.question_text IS NOT NULL
    """)
    questions = cursor.fetchall()  # [(id, question_text, template_id)]

    inserted = 0
    skipped = 0
    errors = []

    for (q_id, q_text, template_id) in questions:
        question_id = f"tq-{q_id}"
        role_id = template_id  # template_id 1 = Java Backend, 2 = React Frontend

        # Idempotency check
        cursor.execute("SELECT id FROM question_embedding WHERE question_id = %s", (question_id,))
        if cursor.fetchone():
            print(f"  [SKIP] question_id={question_id} already exists")
            skipped += 1
            continue

        try:
            embedding = get_embedding(client, q_text)
            embedding_blob = floats_to_blob(embedding)

            cursor.execute("""
                INSERT INTO question_embedding
                  (question_id, role_id, question_text, embedding, created_at, updated_at)
                VALUES (%s, %s, %s, %s, %s, %s)
            """, (question_id, role_id, q_text, embedding_blob, now, now))
            conn.commit()
            print(f"  [INSERT] question_id={question_id} role_id={role_id} dims={len(embedding)}")
            inserted += 1
        except Exception as e:
            errors.append({"question_id": question_id, "error": str(e)})
            print(f"  [ERROR] question_id={question_id}: {e}")

    cursor.close()
    return {"table": "question_embedding", "inserted": inserted, "skipped": skipped, "errors": errors}


def backfill_response_features(conn) -> dict:
    """
    Populate response_feature_cache from interview_message rows that have
    a user_message and evaluation_score. Uses interview_id as session_id
    and 'msg-{id}' as question_id.
    """
    cursor = conn.cursor()
    now_dt = datetime.now(timezone.utc)
    now = now_dt.strftime("%Y-%m-%d %H:%M:%S.%f")

    cursor.execute("""
        SELECT id, interview_id, user_message, evaluation_score,
               technical_accuracy, depth_score, communication_score, experience_score
        FROM interview_message
        WHERE user_message IS NOT NULL AND evaluation_score IS NOT NULL
    """)
    rows = cursor.fetchall()

    inserted = 0
    skipped = 0
    errors = []

    for (msg_id, interview_id, user_message, eval_score,
         tech_acc, depth, comm, exp) in rows:

        session_id = str(interview_id)
        question_id = f"msg-{msg_id}"

        # Idempotency check
        cursor.execute(
            "SELECT id FROM response_feature_cache WHERE session_id = %s AND question_id = %s",
            (session_id, question_id)
        )
        if cursor.fetchone():
            print(f"  [SKIP] session={session_id} question={question_id} already exists")
            skipped += 1
            continue

        # Build feature vector from available scores
        feature_vector = {
            "technical_accuracy": tech_acc,
            "depth_score": depth,
            "communication_score": comm,
            "experience_score": exp,
            "response_length": len(user_message) if user_message else 0,
            "has_technical_terms": 1 if any(w in (user_message or "").lower()
                for w in ["injection", "interface", "class", "component", "hook", "state",
                          "lifecycle", "immutable", "constructor", "framework"]) else 0,
        }

        try:
            cursor.execute("""
                INSERT INTO response_feature_cache
                  (session_id, question_id, response_text, feature_vector,
                   llm_score, predicted_score, prediction_error,
                   model_version, feature_extraction_time_ms,
                   created_at, updated_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, (
                session_id,
                question_id,
                user_message,
                json.dumps(feature_vector),
                eval_score,           # llm_score = actual evaluation score
                eval_score,           # predicted_score = same (no model trained yet)
                0.0,                  # prediction_error = 0 (no prediction gap)
                "backfill-v1.0",
                0,                    # feature_extraction_time_ms
                now,
                now,
            ))
            conn.commit()
            print(f"  [INSERT] session={session_id} question={question_id} score={eval_score}")
            inserted += 1
        except Exception as e:
            errors.append({"question_id": question_id, "error": str(e)})
            print(f"  [ERROR] {e}")

    cursor.close()
    return {"table": "response_feature_cache", "inserted": inserted, "skipped": skipped, "errors": errors}


def main():
    if not OPENAI_API_KEY:
        raise SystemExit("OPENAI_API_KEY not set")
    if not DB_CONFIG["password"]:
        raise SystemExit("DB_PASSWORD not set")

    client = OpenAI(api_key=OPENAI_API_KEY)
    conn = mysql.connector.connect(**DB_CONFIG)
    print(f"Connected to {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}")

    print("\n--- question_embedding backfill ---")
    r1 = backfill_question_embeddings(conn, client)

    print("\n--- response_feature_cache backfill ---")
    r2 = backfill_response_features(conn)

    conn.close()

    print("\n=== Summary ===")
    for r in [r1, r2]:
        print(f"  {r['table']}: inserted={r['inserted']} skipped={r['skipped']} errors={len(r['errors'])}")
        for e in r.get("errors", []):
            print(f"    ERROR: {e}")


if __name__ == "__main__":
    main()
