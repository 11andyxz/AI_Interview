import csv
from statistics import mean

path='eval/results_mitigations/eval_results_20251231_181432.csv'
rows=[]
with open(path,'r',encoding='utf-8') as f:
    r=csv.DictReader(f)
    for row in r:
        rows.append(float(row['latency_ms']))
rows_sorted=sorted(rows)
count=len(rows)
avg=mean(rows)
p50=rows_sorted[int(count*0.5)]
p95=rows_sorted[int(count*0.95)]
print(count)
print(avg)
print(p50)
print(p95)
print(min(rows_sorted))
print(max(rows_sorted))
