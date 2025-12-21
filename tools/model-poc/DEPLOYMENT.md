# Deployment Plan for Open-Source Model Serving

## Executive Summary

This document outlines deployment strategies for self-hosted open-source LLMs as an alternative to OpenAI. It covers serving frameworks, infrastructure requirements, scaling strategies, and cost analysis.

---

## 1. Model Selection

### Recommended Models (Production)

| Model | Size | VRAM | Quality | Use Case |
|-------|------|------|---------|----------|
| Llama-2-7B-Chat | 7B params | 6GB | Good | Development, testing |
| Llama-2-13B-Chat | 13B params | 14GB | Better | Production (low-medium traffic) |
| Mixtral-8x7B-Instruct | 46.7B params | 30GB | Excellent | Production (high quality) |
| Llama-3-8B-Instruct | 8B params | 8GB | Excellent | Production (balanced) |

### Quantization Strategy

- **FP16**: Full precision, highest quality, 2x model size
- **INT8**: Minimal quality loss, ~50% size reduction
- **INT4 (GPTQ/GGUF)**: Good quality, ~75% size reduction
- **Recommendation**: Start with INT4 for cost optimization, use INT8/FP16 if quality metrics insufficient

---

## 2. Serving Frameworks

### Option A: llama.cpp (Lightweight)

**Pros:**
- Minimal dependencies (C++ binary)
- Supports CPU and GPU
- Low memory overhead
- GGUF quantization support
- Easy deployment

**Cons:**
- Limited concurrency handling
- Basic API features
- Less enterprise tooling

**Best For:** Small-scale deployments, testing, cost-sensitive scenarios

**Deployment:**
```bash
# Build with GPU support
git clone https://github.com/ggerganov/llama.cpp
cd llama.cpp
cmake -B build -DLLAMA_CUBLAS=ON
cmake --build build --config Release

# Run server
./build/bin/Release/server.exe -m /path/to/model.gguf \
  --port 8080 --host 0.0.0.0 \
  -c 4096 --threads 8 \
  -ngl 35  # GPU layers
```

### Option B: Text-Generation-WebUI (Development)

**Pros:**
- Rich web interface
- Multiple model format support
- Built-in benchmarking
- Active community

**Cons:**
- Python dependencies overhead
- Not optimized for production
- Resource intensive

**Best For:** Development, model evaluation, experimentation

### Option C: TGI (Text Generation Inference - Recommended)

**Pros:**
- Production-ready (Hugging Face official)
- High throughput (continuous batching)
- Tensor parallelism support
- OpenAPI-compatible REST API
- Built-in monitoring

**Cons:**
- Requires containerization
- Higher complexity

**Best For:** Production deployments with high traffic

**Deployment:**
```bash
# Docker with GPU
docker run --gpus all --shm-size 1g -p 8080:80 \
  -v /models:/data \
  ghcr.io/huggingface/text-generation-inference:latest \
  --model-id /data/llama-2-13b-chat \
  --num-shard 1 \
  --max-batch-prefill-tokens 4096 \
  --max-total-tokens 8192
```

### Option D: vLLM (High Performance)

**Pros:**
- State-of-the-art throughput (PagedAttention)
- Excellent concurrency handling
- Low latency
- Easy scaling

**Cons:**
- GPU-only (no CPU fallback)
- Specific model format requirements

**Best For:** High-traffic production environments, cost optimization via higher GPU utilization

**Deployment:**
```bash
# Install
pip install vllm

# Run server
python -m vllm.entrypoints.openai.api_server \
  --model /path/to/llama-2-13b-chat \
  --port 8080 \
  --tensor-parallel-size 1 \
  --max-model-len 4096
```

### Option E: NVIDIA Triton (Enterprise)

**Pros:**
- Multi-framework support (TensorRT, PyTorch, ONNX)
- Advanced batching strategies
- Model ensemble pipelines
- Enterprise monitoring/metrics

**Cons:**
- Steep learning curve
- Complex configuration
- NVIDIA ecosystem lock-in

**Best For:** Large enterprises with mixed model types, existing NVIDIA infrastructure

---

## 3. Infrastructure Requirements

### Single GPU Server (Entry Level)

**Specs:**
- GPU: NVIDIA A10 (24GB), RTX 4090 (24GB), or L4 (24GB)
- CPU: 8+ cores
- RAM: 32GB+
- Storage: 500GB SSD

**Capacity:**
- Model: Llama-2-13B-Chat (INT4)
- Concurrent users: 10-20
- Throughput: 30-50 requests/minute
- Latency: 500ms - 2s per response

**Cost (Cloud):**
- AWS g5.xlarge: ~$1.00/hour
- GCP n1-standard-4 + T4: ~$0.95/hour
- Azure NC6s_v3: ~$1.14/hour

### Multi-GPU Server (Production)

**Specs:**
- GPU: 2-4x NVIDIA A100 (40GB/80GB)
- CPU: 32+ cores
- RAM: 128GB+
- Storage: 1TB NVMe SSD

**Capacity:**
- Model: Llama-2-70B or Mixtral-8x7B (INT8)
- Concurrent users: 100-200
- Throughput: 200-500 requests/minute
- Latency: 300ms - 1.5s per response

**Cost (Cloud):**
- AWS p4d.24xlarge: ~$32/hour (8x A100)
- GCP a2-highgpu-4g: ~$15/hour (4x A100 40GB)
- Azure NC96ads_A100_v4: ~$27/hour (4x A100 80GB)

### Kubernetes Cluster (Scalable)

**Architecture:**
- GPU node pool: 3-10 nodes (A10/A100)
- CPU node pool: Auto-scaling for proxy/monitoring
- Load balancer: NGINX Ingress or cloud LB
- Model serving: vLLM or TGI pods
- Orchestration: Kubernetes with GPU operator

**Capacity:**
- Elastic scaling based on QPS
- 10-1000+ concurrent users
- Throughput: 500-5000+ requests/minute

**Cost (Cloud):**
- Base cluster: $5-10/hour (control plane + monitoring)
- GPU nodes: $15-100/hour depending on demand
- Storage/networking: $500-2000/month

---

## 4. Scaling Strategy

### Phase 1: Proof of Concept (Current)
- Single GPU server or hosted API
- Model: Llama-2-7B-Chat (INT4)
- Framework: llama.cpp or TGI
- Deployment: Manual on VM or local machine
- Monitoring: Basic logs

### Phase 2: Small Production (1-100 users)
- Single GPU server (A10/T4)
- Model: Llama-2-13B-Chat (INT4/INT8)
- Framework: TGI or vLLM
- Deployment: Docker container
- Monitoring: Prometheus + Grafana
- High availability: Health checks + auto-restart

### Phase 3: Medium Production (100-1000 users)
- 2-4 GPU servers with load balancing
- Model: Llama-2-13B or Mixtral-8x7B
- Framework: vLLM (optimized throughput)
- Deployment: Kubernetes
- Monitoring: Full observability stack
- High availability: Multiple replicas + auto-scaling

### Phase 4: Large Production (1000+ users)
- GPU cluster (10+ nodes)
- Model: Mixture of models (routing based on query complexity)
- Framework: vLLM + Triton
- Deployment: Kubernetes with multi-region
- Monitoring: Distributed tracing, cost analytics
- High availability: Cross-region replication

---

## 5. Cost Analysis

### TCO Comparison: OpenAI vs Self-Hosted

**Assumptions:**
- Average response: 200 tokens
- Average latency requirement: 1-2 seconds
- Monthly usage: 1M requests

#### OpenAI (gpt-3.5-turbo)
- Cost per 1K tokens: $0.0015 (input) + $0.002 (output)
- Average request cost: ~$0.0007
- **Monthly cost: $700**

#### Self-Hosted Llama-2-13B-Chat (Single GPU)
- Infrastructure: AWS g5.2xlarge (~$1.20/hour)
- Monthly runtime: 720 hours (24/7)
- **Monthly cost: $864**
- Break-even: ~1.2M requests/month

#### Self-Hosted Mixtral-8x7B (Multi-GPU)
- Infrastructure: AWS g5.12xlarge (~$5.70/hour)
- Monthly runtime: 720 hours
- **Monthly cost: $4,104**
- Break-even: ~5.9M requests/month

#### Cost Optimization Strategies:
1. **Spot Instances**: 50-70% discount (AWS/GCP)
2. **Reserved Instances**: 30-50% discount (1-3 year commitment)
3. **Hybrid Approach**: Self-hosted for base load, OpenAI for burst traffic
4. **Model Routing**: Use smaller models (7B) for simple queries, larger models (13B/70B) for complex
5. **Batching**: Increase throughput via request batching (reduces per-request cost)

### Cost Tiers (Self-Hosted)

| Monthly Requests | Infrastructure | Monthly Cost | Break-Even vs OpenAI |
|------------------|----------------|--------------|----------------------|
| 100K | 1x A10 (spot) | $150-250 | Not competitive |
| 1M | 1x A10 (reserved) | $500-700 | Competitive |
| 5M | 2x A10 (reserved) | $1,500-2,000 | ~30% savings |
| 10M | 4x A100 (reserved) | $3,000-4,000 | ~40% savings |
| 50M+ | GPU cluster | $10,000-20,000 | ~50-60% savings |

---

## 6. Production Deployment Checklist

### Pre-Deployment
- [ ] Model evaluation complete (quality benchmarks vs OpenAI)
- [ ] Latency targets defined and tested
- [ ] Cost projections validated
- [ ] Infrastructure provisioned (cloud or on-prem)
- [ ] Framework selected and tested
- [ ] Monitoring stack configured

### Deployment
- [ ] Model downloaded and optimized (quantization if needed)
- [ ] Serving framework deployed (TGI/vLLM/Triton)
- [ ] Load balancer configured
- [ ] Health check endpoints tested
- [ ] API authentication implemented
- [ ] Rate limiting configured
- [ ] Logging pipeline active

### Post-Deployment
- [ ] Smoke tests passed (test prompts working)
- [ ] Load testing completed (target QPS verified)
- [ ] Monitoring dashboards reviewed
- [ ] Alert rules configured (latency, error rate, GPU utilization)
- [ ] Incident response plan documented
- [ ] Rollback procedure tested

### Ongoing
- [ ] Daily quality spot-checks (compare sample responses to baseline)
- [ ] Weekly cost analysis (actual vs projected)
- [ ] Monthly model evaluation (consider newer releases)
- [ ] Quarterly capacity planning (scale up/down based on traffic)

---

## 7. Risk Mitigation

### Technical Risks

**Risk: Model quality below OpenAI**
- Mitigation: Establish quality metrics (BLEU, human eval), set acceptance threshold, maintain hybrid approach (OpenAI fallback for critical scenarios)

**Risk: High latency under load**
- Mitigation: Load testing during POC, implement request queuing, use vLLM for better concurrency, add GPU nodes as needed

**Risk: Infrastructure failures**
- Mitigation: Multi-replica deployment, health checks, auto-restart, cross-region for critical services

### Operational Risks

**Risk: Model drift (quality degradation over time)**
- Mitigation: Automated quality monitoring, weekly baseline comparisons, version control for models

**Risk: Cost overruns**
- Mitigation: Budget alerts, auto-scaling limits, spot instances, right-size GPU selection

**Risk: Security vulnerabilities**
- Mitigation: API authentication (JWT), rate limiting, input validation, regular framework updates

---

## 8. Migration Plan (OpenAI -> Self-Hosted)

### Phase 1: Shadow Mode (Month 1)
- Deploy self-hosted model in parallel
- Route 5% of traffic to self-hosted (A/B test)
- Compare quality and latency metrics
- Identify issues and optimize

### Phase 2: Gradual Rollout (Month 2-3)
- Increase traffic to 25%, then 50%, then 75%
- Monitor error rates and user feedback
- Maintain OpenAI as fallback
- Fine-tune model if quality gaps identified

### Phase 3: Full Migration (Month 4)
- Route 95% traffic to self-hosted
- Keep OpenAI for complex/critical queries
- Optimize costs (reserved instances, spot)
- Document lessons learned

### Rollback Plan
- Keep OpenAI credentials active for 6 months
- Implement feature flag for instant fallback
- Monitor quality metrics daily for first 3 months
- Maintain budget for 50% hybrid mode if needed

---

## 9. Recommended Architecture (AI Interview Platform)

```
+-------------------------------------------------------------+
|                      Frontend (React)                       |
+------------------------+------------------------------------+
                         |
                         | HTTPS
                         v
+-------------------------------------------------------------+
|                   API Gateway / Load Balancer               |
|                  (NGINX or Cloud LB)                        |
+----------+--------------------------+-----------------------+
           |                          |
           | Internal                 | Internal
           v                          v
+---------------------+      +-----------------------------+
|  Spring Boot        |      |  Model Serving Cluster      |
|  Backend            |      |  (vLLM or TGI)              |
|  (Java)             |<-----|                             |
|                     |      |  +---------+  +---------+   |
|  - Auth             |      |  | Pod 1   |  | Pod 2   |   |
|  - Interview Logic  |      |  | GPU A10 |  | GPU A10 |   |
|  - Resume Analysis  |      |  +---------+  +---------+   |
+----------+----------+      +-----------------------------+
           |                          |
           |                          |
           v                          v
+---------------------+      +-----------------------------+
|  MySQL Database     |      |  Monitoring Stack           |
|  (User data)        |      |  - Prometheus (metrics)     |
|                     |      |  - Grafana (dashboards)     |
+---------------------+      |  - Loki (logs)              |
                             +-----------------------------+
```

### Component Responsibilities

**API Gateway:**
- SSL termination
- Rate limiting (per user: 60 req/min)
- Request routing (backend vs model)

**Spring Boot Backend:**
- User authentication (JWT)
- Interview session management
- Resume parsing and storage
- Model request orchestration
- Response streaming to frontend

**Model Serving Cluster:**
- LLM inference (Llama-2-13B or Mixtral-8x7B)
- Load balancing across GPU pods
- Auto-scaling based on queue depth
- Health monitoring

**Monitoring:**
- Model response quality metrics
- Latency (p50, p95, p99)
- GPU utilization
- Request volume and error rates
- Cost per request

---

## 10. Next Steps

### Immediate (Week 1-2)
1. Complete POC testing with Llama-2-7B
2. Establish baseline metrics (quality, latency, cost)
3. Compare against OpenAI on 50-100 test cases
4. Document quality gaps and edge cases

### Short-term (Month 1)
1. Provision production GPU server (A10 or L4)
2. Deploy TGI or vLLM with Llama-2-13B-Chat
3. Implement monitoring stack
4. Deploy in shadow mode (5% traffic)
5. Collect user feedback

### Medium-term (Month 2-3)
1. Fine-tune model on interview-specific data (optional)
2. Scale to 50% traffic on self-hosted
3. Optimize costs (reserved instances)
4. Implement hybrid fallback logic

### Long-term (Month 4+)
1. Migrate 95% traffic to self-hosted
2. Evaluate cost savings vs projections
3. Explore larger models (70B) if quality gaps exist
4. Consider model distillation (train smaller model from larger)

---

## Conclusion

Self-hosting open-source LLMs offers significant cost savings (30-60%) at scale (>1M requests/month) with acceptable quality trade-offs. Key success factors:

1. **Start small**: Validate quality in POC before large infrastructure investment
2. **Measure everything**: Establish metrics early (quality, latency, cost)
3. **Hybrid approach**: Keep OpenAI as fallback during transition
4. **Optimize iteratively**: Right-size GPU selection, use quantization, implement batching
5. **Plan for scale**: Use vLLM/TGI with Kubernetes for production

**Recommended First Step:** Deploy Llama-2-13B-Chat (INT4) on single A10 GPU with vLLM, run 30-day shadow test with 10% traffic split.
