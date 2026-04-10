# Week 15 ML Features - Environment Variable Configuration Guide

## Overview

This document standardizes environment variable configuration for Week 14 ML features across all environments (dev, staging, production). This ensures security best practices by externalizing secrets and enabling feature flag control without code changes.

**Target Audience**: DevOps Engineers, SRE, Platform Engineers

**Related Documents**:
- [Week 15 Rollout Runbook](week15_rollout_runbook.md)
- [Week 15 Baseline Monitoring Metrics](week15_baseline_monitoring_metrics.md)

---

## Security Principles

1. **Never commit secrets to Git** - All API keys and passwords must be externalized
2. **Use secrets management** - AWS Secrets Manager, GCP Secret Manager, or HashiCorp Vault
3. **Principle of least privilege** - Each environment has isolated credentials
4. **Rotation policy** - API keys rotated every 90 days
5. **Audit logging** - Log all secret access attempts

---

## Required Environment Variables

### ML Feature Flags

| Variable Name | Type | Default (Prod) | Dev | Staging | Production | Description |
|---------------|------|----------------|-----|---------|------------|-------------|
| `ML_EMBEDDING_ENABLED` | boolean | `false` | `true` | `true` | `false` → `true` | Enable embedding & semantic search |
| `ML_NLP_ENABLED` | boolean | `false` | `true` | `true` | `false` → `true` | Enable NLP feature extraction |
| `ML_PREDICTION_ENABLED` | boolean | `false` | `true` | `true` | `false` → `true` | Enable outcome prediction |
| `ML_ADAPTIVE_ENABLED` | boolean | `false` | `true` | `true` | `false` → `true` | Enable adaptive question selection |

**Rollout Strategy**: Start with all `false` in production, enable progressively per feature after validation.

---

### OpenAI API Configuration

| Variable Name | Type | Required | Example | Description |
|---------------|------|----------|---------|-------------|
| `OPENAI_API_KEY` | string | **YES** | `sk-proj-xxx...` | OpenAI API key for embeddings & chat |
| `OPENAI_CHAT_MODEL` | string | No | `gpt-4-turbo-preview` | Chat completion model name |
| `OPENAI_EMBEDDING_MODEL` | string | No | `text-embedding-3-small` | Embedding model name |

**Security Requirements**:
- Store in secrets manager (e.g., AWS Secrets Manager)
- Rotate every 90 days
- Monitor usage limits in OpenAI dashboard
- Set spending limits to prevent runaway costs

**Local Development**:
```bash
# .env file (DO NOT COMMIT)
export OPENAI_API_KEY="sk-proj-your-dev-key"
export OPENAI_CHAT_MODEL="gpt-4-turbo-preview"
export OPENAI_EMBEDDING_MODEL="text-embedding-3-small"
```

**Production Setup (AWS)**:
```bash
# Store in AWS Secrets Manager
aws secretsmanager create-secret \
  --name /ai-interview/prod/openai-api-key \
  --secret-string "sk-proj-your-prod-key"

# Reference in ECS task definition
{
  "secrets": [
    {
      "name": "OPENAI_API_KEY",
      "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:/ai-interview/prod/openai-api-key"
    }
  ]
}
```

---

### Database Configuration

| Variable Name | Type | Required | Example | Description |
|---------------|------|----------|---------|-------------|
| `DATABASE_URL` | string | **YES** | `jdbc:mysql://prod-db:3306/ai_interview?useSSL=true` | JDBC connection URL |
| `DATABASE_USERNAME` | string | **YES** | `ai_interview_user` | Database username |
| `DATABASE_PASSWORD` | string | **YES** | `xxx` | Database password |

**Security Requirements**:
- Use SSL/TLS for connections (`useSSL=true`)
- Rotate passwords every 90 days
- Store credentials in secrets manager
- Use dedicated user per environment with minimal privileges

---

### Redis Cache Configuration

| Variable Name | Type | Required | Example | Description |
|---------------|------|----------|---------|-------------|
| `REDIS_HOST` | string | Yes | `redis.prod.internal` | Redis server hostname |
| `REDIS_PORT` | integer | No | `6379` | Redis server port |
| `REDIS_PASSWORD` | string | Yes (Prod) | `xxx` | Redis authentication password |

**Security Requirements**:
- Enable Redis AUTH in production
- Use TLS for connections
- Isolate Redis per environment
- Configure maxmemory-policy to avoid OOM

---

### JWT Configuration

| Variable Name | Type | Required | Example | Description |
|---------------|------|----------|---------|-------------|
| `JWT_SECRET` | string | **YES** | `your-256-bit-secret` | Secret key for JWT signing |
| `JWT_EXPIRATION` | integer | No | `86400000` | Token expiration (ms) |

**Security Requirements**:
- Generate: `openssl rand -base64 32`
- Minimum 256 bits (32 bytes)
- Rotate every 6 months
- Never use default values in production

---

### ML Configuration Tuning

| Variable Name | Type | Default | Valid Range | Description |
|---------------|------|---------|-------------|-------------|
| `ML_ADAPTIVE_SE_THRESHOLD` | float | `0.3` | 0.1 - 0.5 | Standard error threshold for adaptive selection |
| `ML_ADAPTIVE_MIN_QUESTIONS` | integer | `8` | 5 - 15 | Minimum questions before early stopping |
| `ML_ADAPTIVE_MAX_QUESTIONS` | integer | `12` | 10 - 20 | Maximum questions per interview |
| `ML_ADAPTIVE_TOPIC_DIVERSITY_WEIGHT` | float | `0.4` | 0.0 - 1.0 | Weight for topic diversity in selection |
| `ML_EMBEDDING_CACHE_TTL_DAYS` | integer | `7` | 1 - 30 | Embedding cache time-to-live |
| `ML_EMBEDDING_BATCH_SIZE` | integer | `100` | 10 - 500 | Batch size for embedding generation |
| `ML_NLP_FEATURE_CACHE_ENABLED` | boolean | `true` | - | Enable NLP feature caching |
| `ML_PREDICTION_EARLY_STOPPING_ENABLED` | boolean | `false` | - | Enable early stopping service |
| `ML_PREDICTION_EARLY_STOPPING_PASS_THRESHOLD` | float | `0.95` | 0.8 - 0.99 | Confidence threshold for early pass |
| `ML_PREDICTION_EARLY_STOPPING_FAIL_THRESHOLD` | float | `0.05` | 0.01 - 0.2 | Confidence threshold for early fail |
| `ML_PREDICTION_EARLY_STOPPING_MIN_QUESTIONS` | integer | `5` | 3 - 10 | Min questions before early stopping |

**Tuning Guidelines**:
- Start with defaults in staging
- Monitor metrics for 1 week
- Adjust thresholds based on false positive/negative rates
- Document changes in runbook

---

## Environment Templates

### Development (.env)

```bash
# Application
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

# Database (local MySQL)
DATABASE_URL=jdbc:mysql://localhost:3306/ai_interview_dev?useSSL=false&serverTimezone=UTC
DATABASE_USERNAME=root
DATABASE_PASSWORD=password

# Redis (local)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# OpenAI
OPENAI_API_KEY=sk-proj-your-dev-key
OPENAI_CHAT_MODEL=gpt-4-turbo-preview
OPENAI_EMBEDDING_MODEL=text-embedding-3-small

# JWT
JWT_SECRET=dev-secret-key-DO-NOT-USE-IN-PROD
JWT_EXPIRATION=86400000

# ML Features (ALL ENABLED for testing)
ML_EMBEDDING_ENABLED=true
ML_NLP_ENABLED=true
ML_PREDICTION_ENABLED=true
ML_ADAPTIVE_ENABLED=true
```

---

### Staging (Kubernetes ConfigMap + Secrets)

**ConfigMap** (`configmap-staging.yaml`):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ai-interview-config-staging
  namespace: ai-interview-staging
data:
  SPRING_PROFILES_ACTIVE: "staging"
  SERVER_PORT: "8080"
  
  # Database (non-sensitive)
  DATABASE_URL: "jdbc:mysql://staging-mysql.internal:3306/ai_interview_staging?useSSL=true&serverTimezone=UTC"
  
  # Redis (non-sensitive)
  REDIS_HOST: "staging-redis.internal"
  REDIS_PORT: "6379"
  
  # OpenAI Models (non-sensitive)
  OPENAI_CHAT_MODEL: "gpt-4-turbo-preview"
  OPENAI_EMBEDDING_MODEL: "text-embedding-3-small"
  
  # ML Features (ENABLED for validation)
  ML_EMBEDDING_ENABLED: "true"
  ML_NLP_ENABLED: "true"
  ML_PREDICTION_ENABLED: "true"
  ML_ADAPTIVE_ENABLED: "true"
  
  # ML Configuration
  ML_ADAPTIVE_SE_THRESHOLD: "0.3"
  ML_ADAPTIVE_MIN_QUESTIONS: "8"
  ML_ADAPTIVE_MAX_QUESTIONS: "12"
  ML_EMBEDDING_CACHE_TTL_DAYS: "7"
  ML_EMBEDDING_BATCH_SIZE: "100"
  ML_PREDICTION_EARLY_STOPPING_ENABLED: "true"
  ML_PREDICTION_EARLY_STOPPING_PASS_THRESHOLD: "0.95"
  ML_PREDICTION_EARLY_STOPPING_FAIL_THRESHOLD: "0.05"
```

**Secret** (`secret-staging.yaml`):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ai-interview-secrets-staging
  namespace: ai-interview-staging
type: Opaque
stringData:
  DATABASE_USERNAME: "ai_interview_staging_user"
  DATABASE_PASSWORD: "staging-db-password-from-vault"
  REDIS_PASSWORD: "staging-redis-password"
  OPENAI_API_KEY: "sk-proj-staging-key-from-vault"
  JWT_SECRET: "staging-jwt-secret-256-bit-from-vault"
```

**Deployment** (`deployment-staging.yaml`):
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ai-interview-backend
  namespace: ai-interview-staging
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ai-interview-backend
  template:
    metadata:
      labels:
        app: ai-interview-backend
    spec:
      containers:
      - name: backend
        image: ai-interview-backend:latest
        envFrom:
        - configMapRef:
            name: ai-interview-config-staging
        - secretRef:
            name: ai-interview-secrets-staging
```

---

### Production (AWS ECS with Secrets Manager)

**Task Definition** (`ecs-task-definition-prod.json`):
```json
{
  "family": "ai-interview-backend-prod",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "containerDefinitions": [
    {
      "name": "backend",
      "image": "123456789.dkr.ecr.us-east-1.amazonaws.com/ai-interview-backend:v1.0.0",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
        {"name": "SERVER_PORT", "value": "8080"},
        {"name": "DATABASE_URL", "value": "jdbc:mysql://prod-rds.xxx.us-east-1.rds.amazonaws.com:3306/ai_interview?useSSL=true"},
        {"name": "REDIS_HOST", "value": "prod-redis.xxx.cache.amazonaws.com"},
        {"name": "REDIS_PORT", "value": "6379"},
        {"name": "OPENAI_CHAT_MODEL", "value": "gpt-4-turbo-preview"},
        {"name": "OPENAI_EMBEDDING_MODEL", "value": "text-embedding-3-small"},
        
        {"name": "ML_EMBEDDING_ENABLED", "value": "false"},
        {"name": "ML_NLP_ENABLED", "value": "false"},
        {"name": "ML_PREDICTION_ENABLED", "value": "false"},
        {"name": "ML_ADAPTIVE_ENABLED", "value": "false"}
      ],
      "secrets": [
        {
          "name": "DATABASE_USERNAME",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:ai-interview/prod/db-username"
        },
        {
          "name": "DATABASE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:ai-interview/prod/db-password"
        },
        {
          "name": "REDIS_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:ai-interview/prod/redis-password"
        },
        {
          "name": "OPENAI_API_KEY",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:ai-interview/prod/openai-api-key"
        },
        {
          "name": "JWT_SECRET",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:ai-interview/prod/jwt-secret"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/ai-interview-backend-prod",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

**Terraform Configuration** (`secrets.tf`):
```hcl
resource "aws_secretsmanager_secret" "openai_api_key" {
  name = "ai-interview/prod/openai-api-key"
  description = "OpenAI API key for ML features"
  recovery_window_in_days = 30
  
  tags = {
    Environment = "production"
    Service = "ai-interview-backend"
  }
}

resource "aws_secretsmanager_secret_version" "openai_api_key" {
  secret_id = aws_secretsmanager_secret.openai_api_key.id
  secret_string = var.openai_api_key  # Passed via terraform.tfvars (not committed)
}

resource "aws_iam_role_policy" "ecs_task_secrets_access" {
  name = "ecs-task-secrets-access"
  role = aws_iam_role.ecs_task_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Effect = "Allow"
        Resource = [
          aws_secretsmanager_secret.openai_api_key.arn,
          # ... other secrets
        ]
      }
    ]
  })
}
```

---

## Validation Checklist

Before deploying to each environment, validate configuration:

### Development
- [ ] `.env` file created (not committed to Git)
- [ ] All secrets are non-production placeholders
- [ ] Database points to local MySQL
- [ ] Redis points to localhost
- [ ] All ML features enabled

### Staging
- [ ] ConfigMap created in Kubernetes/ECS
- [ ] Secrets stored in secrets manager
- [ ] Database points to staging RDS/Cloud SQL
- [ ] Redis points to staging ElastiCache/Memorystore
- [ ] All ML features enabled
- [ ] Monitoring/observability configured
- [ ] Log aggregation working

### Production
- [ ] All secrets stored in AWS Secrets Manager / GCP Secret Manager
- [ ] IAM roles configured for secret access
- [ ] Database points to production RDS/Cloud SQL
- [ ] Redis points to production ElastiCache/Memorystore
- [ ] All ML features **disabled by default**
- [ ] Monitoring/alerting configured
- [ ] Log aggregation and retention configured
- [ ] Backup and disaster recovery tested

---

## Security Audit Log

Document all secret rotations and access:

| Date | Secret | Rotated By | Reason | Environments Affected |
|------|--------|------------|--------|----------------------|
| 2025-01-18 | OPENAI_API_KEY | DevOps Team | Initial setup | staging, production |
| | | | | |

---

## Troubleshooting

### Issue: Application fails to start with "Bean creation error"

**Cause**: ML feature flag set to `true` but required dependency (e.g., Redis) is unavailable.

**Solution**:
1. Check environment variables: `kubectl get configmap -n <namespace>`
2. Verify Redis connectivity: `redis-cli -h $REDIS_HOST ping`
3. Check logs: `kubectl logs <pod-name> | grep ConditionalOnProperty`

### Issue: "401 Unauthorized" from OpenAI API

**Cause**: `OPENAI_API_KEY` is invalid or not set.

**Solution**:
1. Verify secret exists: `aws secretsmanager get-secret-value --secret-id /ai-interview/prod/openai-api-key`
2. Check ECS task IAM role has `secretsmanager:GetSecretValue` permission
3. Verify environment variable is injected: `kubectl exec <pod-name> -- env | grep OPENAI`

### Issue: High embedding costs in production

**Cause**: `ML_EMBEDDING_ENABLED=true` when it should be `false`.

**Solution**:
1. Immediately disable: `kubectl set env deployment/ai-interview-backend ML_EMBEDDING_ENABLED=false`
2. Verify cache is working: Check `ml.embedding.cache.hit_ratio` metric
3. Investigate usage spike in OpenAI dashboard

---

## Best Practices

1. **Never hardcode secrets** - Always use environment variables
2. **Use different API keys per environment** - Isolate dev/staging/prod
3. **Implement secret rotation** - Automate 90-day rotation
4. **Audit secret access** - Enable CloudTrail / Audit Logs
5. **Principle of least privilege** - Grant minimal IAM permissions
6. **Test in staging first** - Validate all config changes before production
7. **Document all changes** - Update this guide after configuration changes
8. **Use infrastructure as code** - Terraform/CloudFormation for reproducibility

---

**Document Version**: 1.0  
**Last Updated**: 2025-01-18  
**Owner**: Platform Engineering Team  
**Review Cycle**: After each secret rotation or environment change
