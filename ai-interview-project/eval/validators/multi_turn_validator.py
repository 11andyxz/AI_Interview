#!/usr/bin/env python3
"""Validator for multi_turn outputs (interview_turn items).
"""
import json
import os

SCHEMA_PATH = os.path.join(os.path.dirname(__file__), '..', 'schemas', 'interview_turn_schema.json')


def load_schema():
    try:
        with open(SCHEMA_PATH, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception:
        return None


def validate(output):
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

    # basic required check
    for req in schema.get('required', []):
        if req not in obj:
            return False, 'schema_error', f'missing:{req}'

    # types
    props = schema.get('properties', {})
    for k, rules in props.items():
        if k not in obj:
            continue
        val = obj[k]
        t = rules.get('type')
        if t == 'string' and not isinstance(val, str):
            return False, 'schema_error', f'type:{k}'
        if t == 'integer' and not isinstance(val, int):
            return False, 'schema_error', f'type:{k}'

    return True, None, ''
