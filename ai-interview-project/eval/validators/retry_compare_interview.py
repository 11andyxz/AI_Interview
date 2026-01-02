#!/usr/bin/env python3
"""
Attempt a local 'retry' by salvaging JSON from a malformed model output
for a single failed prompt (intv-003), validate before/after, and write a CSV
comparison. Does not call backend — works offline on `sample_outputs`.
"""
import json
import csv
import re
from pathlib import Path
import sys

ROOT = Path(__file__).parent.parent
SAMPLES = ROOT / 'sample_outputs' / 'interview_outputs.jsonl'
SCHEMA = ROOT / 'schemas' / 'interview_chat_schema.json'
OUT_CSV = ROOT / 'results' / 'retry_comparison_intv-003.csv'

from interview_validator import load_schema, classify_and_validate


def extract_json_by_brace(text):
    # Find the first balanced JSON object by scanning braces
    starts = [m.start() for m in re.finditer(r"\{", text)]
    for s in starts:
        depth = 0
        for i in range(s, len(text)):
            if text[i] == '{':
                depth += 1
            elif text[i] == '}':
                depth -= 1
                if depth == 0:
                    candidate = text[s:i+1]
                    try:
                        json.loads(candidate)
                        return candidate
                    except Exception:
                        break
    return None


def try_salvage(original):
    # common case: ```json ... ```
    m = re.search(r'```\s*json\s*(.*?)```', original, re.DOTALL | re.IGNORECASE)
    if m:
        return m.group(1).strip()

    # plain bracket search
    cand = extract_json_by_brace(original)
    if cand:
        return cand

    # fallback: find first '[' ... ']' array
    m2 = re.search(r"\[.*\]", original, re.DOTALL)
    if m2:
        return m2.group(0)

    return None


def main():
    schema = load_schema(str(SCHEMA))
    target_id = 'intv-003'
    entry = None
    with open(SAMPLES, 'r', encoding='utf-8') as f:
        for line in f:
            if not line.strip():
                continue
            obj = json.loads(line)
            if obj.get('prompt_id') == target_id:
                entry = obj
                break

    if not entry:
        print('Target prompt not found in sample outputs')
        sys.exit(2)

    original = entry.get('output') or ''

    ok1, et1, info1 = classify_and_validate(schema, original)

    salvaged = try_salvage(original)
    ok2 = False
    et2 = ''
    info2 = ''
    salvaged_text = ''
    if salvaged:
        salvaged_text = salvaged
        ok2, et2, info2 = classify_and_validate(schema, salvaged)

    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT_CSV, 'w', newline='', encoding='utf-8') as cf:
        writer = csv.writer(cf)
        writer.writerow(['prompt_id', 'stage', 'status', 'error_type', 'info', 'sample'])
        writer.writerow([target_id, 'original', 'pass' if ok1 else 'fail', et1 or '', info1 or '', original.replace('\n','\\n')])
        writer.writerow([target_id, 'salvaged_attempted', 'yes' if salvaged else 'no', '', '', salvaged_text.replace('\n','\\n')])
        if salvaged:
            writer.writerow([target_id, 'salvaged_validated', 'pass' if ok2 else 'fail', et2 or '', info2 or '', salvaged_text.replace('\n','\\n')])

    print(f"Original pass={ok1} ({et1})")
    if salvaged:
        print(f"Salvaged pass={ok2} ({et2})")
    else:
        print('No salvage candidate found')

    print(f"Wrote comparison CSV: {OUT_CSV}")


if __name__ == '__main__':
    main()
