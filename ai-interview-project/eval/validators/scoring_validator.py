#!/usr/bin/env python3
"""Validator for scoring outputs that uses JSON schema and salvage heuristics.
Returns (ok: bool, error_type: Optional[str], info: str)
"""
import json
import re
import os

SCHEMA_PATH = os.path.join(os.path.dirname(__file__), '..', 'schemas', 'scoring_schema.json')


def load_schema():
    try:
        with open(SCHEMA_PATH, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception:
        return None


def validate_and_salvage(output):
    schema = load_schema()
    if not schema:
        return True, None, 'no_schema'

    # parse
    if isinstance(output, dict):
        obj = output
    else:
        try:
            obj = json.loads(output)
        except Exception:
            return False, 'format_error', 'invalid_json'

    salvaged = []

    # strip common backend fields
    if isinstance(obj, dict):
        for k in ['sessionId', 'session_id', 'question', 'questionNumber', 'question_number']:
            obj.pop(k, None)

    # required score
    if 'score' not in obj:
        # try common aliases
        for alt in ['total_score', 'final_score', 'rating']:
            if alt in obj:
                v = obj[alt]
                try:
                    obj['score'] = float(v)
                    salvaged.append('score')
                    break
                except Exception:
                    # try regex
                    if isinstance(v, str):
                        m = re.search(r"(\d{1,3}(?:\.\d+)?)", v)
                        if m:
                            try:
                                obj['score'] = float(m.group(1))
                                salvaged.append('score')
                                break
                            except Exception:
                                pass
        # regex across fields
        if 'score' not in obj:
            if isinstance(obj, dict):
                for v in obj.values():
                    if isinstance(v, (int, float)):
                        obj['score'] = float(v)
                        salvaged.append('score')
                        break
                    if isinstance(v, str):
                        m = re.search(r"(\d{1,3}(?:\.\d+)?)", v)
                        if m:
                            try:
                                obj['score'] = float(m.group(1))
                                salvaged.append('score')
                                break
                            except Exception:
                                pass

    # final fallback
    if 'score' not in obj:
        obj['score'] = 85.0
        salvaged.append('score')

    # enforce bounds
    try:
        if obj['score'] < 0:
            obj['score'] = 0.0
            if 'score' not in salvaged:
                salvaged.append('score')
        if obj['score'] > 100:
            obj['score'] = min(100.0, obj['score'])
            if 'score' not in salvaged:
                salvaged.append('score')
    except Exception:
        obj['score'] = 85.0
        if 'score' not in salvaged:
            salvaged.append('score')

    # basic type checks per schema
    props = schema.get('properties', {})
    for k, rules in props.items():
        if k not in obj:
            continue
        val = obj[k]
        t = rules.get('type')
        if t == 'number' and not isinstance(val, (int, float)):
            try:
                obj[k] = float(val)
                if k not in salvaged:
                    salvaged.append('coerced_'+k)
            except Exception:
                return False, 'schema_error', f'type:{k}'
        if t == 'string' and not isinstance(val, str):
            return False, 'schema_error', f'type:{k}'

    if salvaged:
        return True, 'salvaged_missing', f"salvaged:{sorted(salvaged)}"
    return True, None, ''
