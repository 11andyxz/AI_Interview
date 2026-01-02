import importlib.util
import os
import json

this_dir = os.path.dirname(__file__)
mod_path = os.path.join(this_dir, 'concurrency_test.py')
spec = importlib.util.spec_from_file_location('concurrency_test', mod_path)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

runs = [
    {'workers': 5, 'requests': 50, 'out': os.path.join(this_dir, 'results', 'concurrency_5.json')},
    {'workers': 10, 'requests': 50, 'out': os.path.join(this_dir, 'results', 'concurrency_10.json')}
]

for r in runs:
    print(f"Running concurrency test: workers={r['workers']}, requests={r['requests']}")
    metrics = mod.run_concurrency_test(num_requests=r['requests'], num_workers=r['workers'])
    mod.print_results(metrics)
    os.makedirs(os.path.dirname(r['out']), exist_ok=True)
    with open(r['out'], 'w', encoding='utf-8') as f:
        json.dump(metrics, f, indent=2, ensure_ascii=False)
    print(f"Saved: {r['out']}")
